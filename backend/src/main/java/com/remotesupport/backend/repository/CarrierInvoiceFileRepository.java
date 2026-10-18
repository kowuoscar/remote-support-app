package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.CarrierInvoiceFile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarrierInvoiceFileRepository extends JpaRepository<CarrierInvoiceFile, UUID> {

  List<CarrierInvoiceFile> findByClientInvoiceIdOrderByUploadedAtAsc(UUID clientInvoiceId);

  boolean existsByClientInvoiceIdAndSimCardId(UUID clientInvoiceId, UUID simCardId);

  /** Scoped by the file's own Client Invoice's Contract, for a direct-by-id download lookup. */
  Optional<CarrierInvoiceFile> findByIdAndClientInvoiceContractId(UUID id, UUID contractId);

  /**
   * Scoped to one specific Client Invoice (client-invoice-submission-and-visibility ticket): once
   * a Tester can view a sent/approved invoice, its file downloads must be scoped to that exact
   * invoice, not merely "any file on this Contract" — see {@code
   * ClientInvoiceController#downloadFile}.
   */
  Optional<CarrierInvoiceFile> findByIdAndClientInvoiceId(UUID id, UUID clientInvoiceId);

  /**
   * The files retention must delete: not yet deleted, on an APPROVED Client Invoice approved at or
   * before {@code cutoff}, oldest approval first. Holds no lock.
   */
  @Query(
      """
      select new com.remotesupport.backend.repository.DueCarrierInvoiceFile(f.id, f.storagePath)
      from CarrierInvoiceFile f join f.clientInvoice ci
      where f.deletedAt is null
        and ci.status = com.remotesupport.backend.domain.ClientInvoiceStatus.APPROVED
        and ci.approvedAt <= :cutoff
      order by ci.approvedAt asc, f.id asc
      """)
  List<DueCarrierInvoiceFile> findDue(@Param("cutoff") Instant cutoff, Pageable page);

  /** Marks one file deleted; 0 when another run already did. Callers run it in their own transaction. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update CarrierInvoiceFile f set f.deletedAt = :at where f.id = :id and f.deletedAt is null")
  int markDeleted(@Param("id") UUID id, @Param("at") Instant at);
}
