package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/** {@link CarrierInvoiceFileStorage} over a mocked {@link S3Client}: the failures no real store shows on demand. */
class CarrierInvoiceFileStorageTest {

  private S3Client s3;
  private CarrierInvoiceFileStorage storage;

  @BeforeEach
  void setUp() {
    s3 = mock(S3Client.class);
    storage =
        new CarrierInvoiceFileStorage(
            s3, new ObjectStoreProperties("http://store:8333", "us-east-1", "the-bucket", "ak", "sk", true, true));
  }

  @Test
  void keyForHasTheExactShape() {
    UUID tenant = UUID.randomUUID();
    UUID invoice = UUID.randomUUID();
    UUID file = UUID.randomUUID();

    assertThat(storage.keyFor(tenant, invoice, file)).isEqualTo("carrier-invoices/" + tenant + "/" + invoice + "/" + file);
  }

  @Test
  void putSendsTheBytesToTheBucketWithNoMetadataOrContentType() {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());

    storage.put("k/1", new MockMultipartFile("file", "a.pdf", "application/pdf", "abc".getBytes()));

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(request.capture(), any(RequestBody.class));
    assertThat(request.getValue().bucket()).isEqualTo("the-bucket");
    assertThat(request.getValue().key()).isEqualTo("k/1");
    assertThat(request.getValue().hasMetadata()).isFalse();
    assertThat(request.getValue().tagging()).isNull();
    assertThat(request.getValue().contentType()).isNull();
    assertThat(request.getValue().contentDisposition()).isNull();
  }

  @Test
  void anSdkFailureOnPutBecomesObjectStoreUnavailable() {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(SdkClientException.create("down"));

    assertThatThrownBy(() -> storage.put("k/1", new MockMultipartFile("file", "a.pdf", "application/pdf", "abc".getBytes())))
        .isInstanceOf(ObjectStoreUnavailableException.class);
  }

  @Test
  void anSdkFailureOnReadBecomesObjectStoreUnavailable() {
    when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(SdkClientException.create("down"));

    assertThatThrownBy(() -> storage.read("k/1")).isInstanceOf(ObjectStoreUnavailableException.class);
  }

  @Test
  void anSdkFailureOnDeleteBecomesObjectStoreUnavailable() {
    when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(SdkClientException.create("down"));

    assertThatThrownBy(() -> storage.delete("k/1")).isInstanceOf(ObjectStoreUnavailableException.class);
  }

  @Test
  void noSuchKeyOnReadBecomesCarrierInvoiceFileMissing() {
    when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

    assertThatThrownBy(() -> storage.read("k/1")).isInstanceOf(CarrierInvoiceFileMissingException.class);
  }

  @Test
  void deletingAKeyThatIsNotThereSucceeds() {
    when(s3.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

    assertThatCode(() -> storage.delete("k/missing")).doesNotThrowAnyException();
  }
}
