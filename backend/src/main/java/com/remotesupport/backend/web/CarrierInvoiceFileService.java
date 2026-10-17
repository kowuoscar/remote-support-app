package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.CarrierInvoiceFile;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.dto.CarrierInvoiceFileResponse;
import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * The Carrier Invoice File operations that touch the object store. Deliberately not
 * transactional: no store call may run inside the transaction that holds a Client Invoice row lock
 * (Backend 8), or a 20MB upload would stall a send. So an attach uploads first, then saves the row
 * under the lock in {@link ClientInvoiceService#recordAttachedFile}, and deletes the object again if
 * that fails for any reason. Undoing a failed attach is the only order in which a reader never sees
 * a row without its object.
 */
@Component
public class CarrierInvoiceFileService {

  private static final Logger log = LoggerFactory.getLogger(CarrierInvoiceFileService.class);

  private final CarrierInvoiceFileStorage storage;
  private final ClientInvoiceService clientInvoiceService;
  private final CarrierInvoiceFileRepository carrierInvoiceFileRepository;
  private final ObjectStoreAvailability availability;

  public CarrierInvoiceFileService(
      CarrierInvoiceFileStorage storage,
      ClientInvoiceService clientInvoiceService,
      CarrierInvoiceFileRepository carrierInvoiceFileRepository,
      ObjectStoreAvailability availability) {
    this.availability = availability;
    this.storage = storage;
    this.clientInvoiceService = clientInvoiceService;
    this.carrierInvoiceFileRepository = carrierInvoiceFileRepository;
  }

  /**
   * Attaches a Carrier Invoice File to a found invoice: the bytes go to the store, then the row is
   * saved under the invoice's row lock (a sent invoice answers 409). Any failure after the upload
   * deletes the object and rethrows; a failing delete is a WARN naming the stray key and never
   * hides the original exception. Callers run {@link ClientInvoiceService#requireNonEmpty} first.
   */
  public ResponseEntity<CarrierInvoiceFileResponse> attach(
      ClientInvoice invoice, MultipartFile file, UUID simCardId, AuthenticatedPrincipal principal) {
    requireStore();
    UUID fileId = UUID.randomUUID();
    String key = storage.keyFor(invoice.getTenant().getId(), invoice.getId(), fileId);
    storage.put(key, file);
    try {
      return clientInvoiceService.recordAttachedFile(invoice, fileId, key, file, simCardId, principal);
    } catch (RuntimeException e) {
      try {
        storage.delete(key);
      } catch (RuntimeException deleteFailure) {
        log.warn("Stray object left in the store after a failed attach, no row refers to it: {}", key);
      }
      throw e;
    }
  }

  /**
   * Removes a file from a draft open to its Agent: 503 at once while the store is unavailable, then
   * the row is deleted under the invoice's row lock ({@link ClientInvoiceService#removeFile}), and
   * only after that commit the object. A failing object delete is a WARN naming the stray key; the
   * file is gone from the invoice, so the answer stands.
   */
  public void remove(ClientInvoice invoice, UUID fileId, AuthenticatedPrincipal principal) {
    requireStore();
    String key = clientInvoiceService.removeFile(invoice, fileId, principal);
    try {
      storage.delete(key);
    } catch (RuntimeException e) {
      log.warn("Carrier invoice file object left in the store, stray object, no row refers to it: {}", key);
    }
  }

  /** One of this invoice's Carrier Invoice Files; a file of any other invoice is 404. */
  public ResponseEntity<byte[]> download(ClientInvoice invoice, UUID fileId) {
    CarrierInvoiceFile file =
        carrierInvoiceFileRepository
            .findByIdAndClientInvoiceId(fileId, invoice.getId())
            .orElseThrow(() -> new NotFoundException("No carrier invoice file with id " + fileId));
    requireStore();
    byte[] content = storage.read(file.getStoragePath());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.getContentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.getFilename(), StandardCharsets.UTF_8).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(content);
  }

  /** 503 at once, without a store call, while file storage is unavailable. */
  private void requireStore() {
    if (!availability.isAvailable()) {
      throw new ObjectStoreUnavailableException("Object store is unavailable");
    }
  }
}
