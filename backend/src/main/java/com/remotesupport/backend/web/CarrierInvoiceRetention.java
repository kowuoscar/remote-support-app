package com.remotesupport.backend.web;

import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.repository.DueCarrierInvoiceFile;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes the object of every Carrier Invoice File whose Client Invoice was approved at least 30
 * days ago, and marks the row {@code deleted_at}; the row stays (ADR 0007, dated note). The rule is
 * evaluated on every run from the invoice's current status and {@code approvedAt}, so nothing about
 * the deadline is stored.
 *
 * <p>Deliberately not transactional as a whole: the select and each row's mark run in their own
 * short transactions, and no store call runs inside one (Backend 8). The job never probes
 * availability and never sets file storage available; a failed delete marks it unavailable through
 * {@link CarrierInvoiceFileStorage}'s existing path. {@link #runOnce()} works whatever {@link
 * CarrierInvoiceRetentionProperties#enabled()} says; the flag only governs the triggers.
 */
@Component
public class CarrierInvoiceRetention {

  /** How long a file's bytes are kept after its invoice's approval: 30 x 24 hours. */
  static final Duration RETENTION = Duration.ofDays(30);

  /** The most files one run takes. */
  static final int BATCH_SIZE = 500;

  private static final Logger log = LoggerFactory.getLogger(CarrierInvoiceRetention.class);

  /**
   * What one run found and did.
   *
   * @param due the files it took (at most {@value #BATCH_SIZE})
   * @param deleted the files whose object it deleted and whose row it marked
   * @param stoppedByStore whether it stopped early because the object store was unavailable
   */
  public record RetentionRun(int due, int deleted, boolean stoppedByStore) {}

  private final CarrierInvoiceFileRepository repository;
  private final CarrierInvoiceFileStorage storage;
  private final TransactionTemplate readTransaction;
  private final TransactionTemplate writeTransaction;

  public CarrierInvoiceRetention(
      CarrierInvoiceFileRepository repository,
      CarrierInvoiceFileStorage storage,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.storage = storage;
    this.readTransaction = new TransactionTemplate(transactionManager);
    this.readTransaction.setReadOnly(true);
    this.writeTransaction = new TransactionTemplate(transactionManager);
  }

  /** One run: up to {@value #BATCH_SIZE} due files, oldest approval first, stopping at the first store failure. */
  public RetentionRun runOnce() {
    Instant now = Instant.now();
    Instant cutoff = now.minus(RETENTION);
    List<DueCarrierInvoiceFile> due =
        readTransaction.execute(status -> repository.findDue(cutoff, PageRequest.of(0, BATCH_SIZE)));
    if (due == null || due.isEmpty()) {
      log.debug("Carrier invoice retention: nothing due");
      return new RetentionRun(0, 0, false);
    }

    int deleted = 0;
    for (DueCarrierInvoiceFile file : due) {
      try {
        storage.delete(file.storagePath());
      } catch (ObjectStoreUnavailableException e) {
        // The storage has logged its one ERROR and marked file storage unavailable.
        log.warn(
            "Carrier invoice retention stopped: object store unavailable; {} deleted, {} still due; next run retries",
            deleted,
            due.size() - deleted);
        return new RetentionRun(due.size(), deleted, true);
      }
      writeTransaction.executeWithoutResult(status -> repository.markDeleted(file.id(), now));
      deleted++;
      log.debug("Carrier invoice retention deleted file {} at key {}", file.id(), file.storagePath());
    }
    log.info("Carrier invoice retention deleted {} files", deleted);
    return new RetentionRun(due.size(), deleted, false);
  }
}
