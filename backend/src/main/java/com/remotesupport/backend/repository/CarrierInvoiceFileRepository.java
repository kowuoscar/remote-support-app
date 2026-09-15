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
}
