package com.remotesupport.backend.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * The bytes of Carrier Invoice Files, kept as opaque objects in one private S3-compatible bucket
 * (ADR 0007). The row ({@code carrier_invoice_files}) holds the object key and everything a person
 * sees: name, type, size, owner; the object carries no user metadata, no tags, and no {@code
 * Content-Type} of its own. The browser never reaches the store: the backend writes and reads.
 *
 * <p>The write happens before the row is saved, outside any transaction, and {@link
 * CarrierInvoiceFileService} deletes the object again if the row cannot be saved, so a reader never
 * sees a row without its object.
 */
@Component
public class CarrierInvoiceFileStorage {

  private static final Logger log = LoggerFactory.getLogger(CarrierInvoiceFileStorage.class);

  private final S3Client s3;
  private final ObjectStoreProperties props;
  private final ObjectStoreAvailability availability;

  public CarrierInvoiceFileStorage(
      S3Client s3, ObjectStoreProperties props, ObjectStoreAvailability availability) {
    this.availability = availability;
    this.s3 = s3;
    this.props = props;
  }

  /** {@code carrier-invoices/{tenant id}/{client invoice id}/{file id}}. */
  public String keyFor(UUID tenantId, UUID clientInvoiceId, UUID fileId) {
    return "carrier-invoices/" + tenantId + "/" + clientInvoiceId + "/" + fileId;
  }

  /**
   * Streams the upload to {@code key}, its size taken from the part. The SDK may retry, so the body
   * is a content provider that opens a fresh stream of the part on each call (closing the previous
   * one); a single stream could not be rewound. Failing to read the part is not the store's fault:
   * it is not reported as unavailability.
   */
  public void put(String key, MultipartFile file) {
    ReopeningProvider provider = new ReopeningProvider(file);
    provider.newStream(); // an unreadable part fails here, before the SDK could mistake it for an outage
    try {
      s3.putObject(
          PutObjectRequest.builder().bucket(props.bucket()).key(key).build(),
          RequestBody.fromContentProvider(provider, file.getSize(), "application/octet-stream"));
    } catch (SdkException e) {
      throw unavailable("PutObject", key, e);
    } finally {
      provider.close();
    }
  }

  /** Hands the SDK a new stream of the part per call, closing the one it handed before. */
  private static final class ReopeningProvider implements ContentStreamProvider {
    private final MultipartFile file;
    private InputStream current;

    ReopeningProvider(MultipartFile file) {
      this.file = file;
    }

    void close() {
      try {
        if (current != null) {
          current.close();
        }
      } catch (IOException ignored) {
        // nothing left to read
      }
    }

    @Override
    public InputStream newStream() {
      try {
        if (current != null) {
          current.close();
        }
        current = file.getInputStream();
        return current;
      } catch (IOException e) {
        throw new UncheckedIOException("Could not read the uploaded file", e);
      }
    }
  }

  /** The whole object; {@link CarrierInvoiceFileMissingException} when there is none at the key. */
  public byte[] read(String key) {
    try {
      return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(props.bucket()).key(key).build())
          .asByteArray();
    } catch (NoSuchKeyException e) {
      log.warn("Carrier invoice file object is missing: {}", key);
      throw new CarrierInvoiceFileMissingException(key);
    } catch (SdkException e) {
      throw unavailable("GetObject", key, e);
    }
  }

  /** Idempotent: deleting a key that is not there succeeds. */
  public void delete(String key) {
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket()).key(key).build());
    } catch (SdkException e) {
      throw unavailable("DeleteObject", key, e);
    }
  }

  /** Logs the failure once, naming the operation, key, endpoint and bucket; never the credentials or the SDK's message. */
  private ObjectStoreUnavailableException unavailable(String operation, String key, Exception cause) {
    String detail =
        cause instanceof S3Exception s3e ? "HTTP " + s3e.statusCode() : cause.getClass().getSimpleName();
    availability.markUnavailable();
    log.error(
        "Object store {} failed for key {} at endpoint {} bucket {} ({})",
        operation,
        key,
        props.endpoint(),
        props.bucket(),
        detail);
    return new ObjectStoreUnavailableException("Object store is unavailable", cause);
  }
}
