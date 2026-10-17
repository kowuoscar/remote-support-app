package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

/** The real SDK client against an endpoint nothing listens on: its own retries must not hide the outage. */
@ExtendWith(OutputCaptureExtension.class)
class CarrierInvoiceFileStorageUnreachableStoreTest {

  private static final ObjectStoreProperties PROPS =
      new ObjectStoreProperties("http://localhost:1", "us-east-1", "the-bucket", "secret-ak-789", "secret-sk-012", true, true);

  @Test
  void anAttachToAnUnreachableStoreIsUnavailableLoggedOnceAndMarksTheStoreDown(CapturedOutput output) {
    ObjectStoreAvailability availability = mock(ObjectStoreAvailability.class);
    try (S3Client s3 = new ObjectStoreConfig().s3Client(PROPS)) {
      CarrierInvoiceFileStorage storage = new CarrierInvoiceFileStorage(s3, PROPS, availability);

      assertThatThrownBy(
              () -> storage.put("k/1", new MockMultipartFile("file", "a.pdf", "application/pdf", "abc".getBytes())))
          .isInstanceOf(ObjectStoreUnavailableException.class);
    }

    verify(availability).markUnavailable();
    var errors = output.getAll().lines().filter(l -> l.contains(" ERROR ")).toList();
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("PutObject").contains("http://localhost:1").contains("the-bucket");
    assertThat(output.getAll()).doesNotContain("secret-ak-789").doesNotContain("secret-sk-012");
  }

  @Test
  void theUploadIsStreamedNotReadWholeAndEachRetryReopensIt() throws Exception {
    MultipartFile part = mock(MultipartFile.class);
    when(part.getBytes()).thenThrow(new AssertionError("the part must not be read whole into memory"));
    when(part.getSize()).thenReturn(3L);
    when(part.getContentType()).thenReturn("application/pdf");
    when(part.getInputStream()).thenAnswer(inv -> new ByteArrayInputStream("abc".getBytes()));
    ObjectStoreAvailability availability = mock(ObjectStoreAvailability.class);
    try (S3Client s3 = new ObjectStoreConfig().s3Client(PROPS)) {
      CarrierInvoiceFileStorage storage = new CarrierInvoiceFileStorage(s3, PROPS, availability);

      assertThatThrownBy(() -> storage.put("k/1", part)).isInstanceOf(ObjectStoreUnavailableException.class);
    }

    verify(part, atLeast(2)).getInputStream();
    verify(availability).markUnavailable();
  }
}
