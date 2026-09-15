package com.remotesupport.backend.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Filesystem-backed storage for carrier invoice files attached to a Client Invoice draft
 * (client-invoice-generation ticket AC: "Agent can attach one or more carrier invoice files to
 * the draft Client Invoice"). Simple local storage under a directory in the backend's working
 * dir, per the ticket's own note that cloud storage is explicitly out of scope for this MVP.
 *
 * <p>Chosen over a database blob column: a carrier invoice PDF can run several megabytes, and
 * routing that through Hibernate/JPA on every {@code ClientInvoice} read (even one that never
 * touches the file) would be wasted I/O the moment {@link CarrierInvoiceFile} rows are looked up
 * alongside a draft, whereas the filesystem only pays that cost when a file is actually uploaded
 * or downloaded. The trade-off: the database row and the file on disk are now two things that can
 * drift (e.g. the row commits but the disk write fails, or vice versa) — acceptable here because
 * {@link com.remotesupport.backend.web.ClientInvoiceController#uploadFile} writes the file before
 * saving the row, so a failed write never leaves an orphaned DB reference, and there is no
 * multi-instance deployment in this MVP for the directory to need to be shared. The stored {@code
 * storagePath} is an opaque handle a real deployment could point at any blob store later (S3, GCS)
 * without a schema change — only this class's {@code store}/{@code read} implementation changes.
 */
@Component
public class CarrierInvoiceFileStorage {

  private final Path rootDir;

  public CarrierInvoiceFileStorage(
      @Value("${app.file-storage.carrier-invoices-dir:./data/carrier-invoice-files}") String rootDir) {
    this.rootDir = Path.of(rootDir);
  }

  /**
   * Writes the file under a per-Client-Invoice subdirectory, named by the file's own generated id
   * rather than its (attacker-influenceable) original filename — the original name is preserved
   * only as {@link CarrierInvoiceFile#filename} metadata, never used as a path segment. Returns
   * the path to store as {@code storagePath}.
   */
  public String store(UUID clientInvoiceId, UUID fileId, MultipartFile file) throws IOException {
    Path dir = rootDir.resolve(clientInvoiceId.toString());
    Files.createDirectories(dir);
    Path target = dir.resolve(fileId.toString());
    try (InputStream in = file.getInputStream()) {
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
    return target.toString();
  }

  public byte[] read(String storagePath) throws IOException {
    return Files.readAllBytes(Path.of(storagePath));
  }
}
