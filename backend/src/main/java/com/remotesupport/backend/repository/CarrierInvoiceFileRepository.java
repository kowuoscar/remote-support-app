package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.CarrierInvoiceFile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrierInvoiceFileRepository extends JpaRepository<CarrierInvoiceFile, UUID> {

  List<CarrierInvoiceFile> findByClientInvoiceIdOrderByUploadedAtAsc(UUID clientInvoiceId);

  /** Scoped by the file's own Client Invoice's Contract, for a direct-by-id download lookup. */
  Optional<CarrierInvoiceFile> findByIdAndClientInvoiceContractId(UUID id, UUID contractId);

  /**
   * Scoped to one specific Client Invoice (client-invoice-submission-and-visibility ticket): once
   * a Tester can view a sent/approved invoice, its file downloads must be scoped to that exact
   * invoice, not merely "any file on this Contract" — see {@code
   * ClientInvoiceController#downloadFile}.
   */
  Optional<CarrierInvoiceFile> findByIdAndClientInvoiceId(UUID id, UUID clientInvoiceId);
}
