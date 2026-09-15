package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.ClientInvoice;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientInvoiceRepository extends JpaRepository<ClientInvoice, UUID> {

  Optional<ClientInvoice> findByContractIdAndBillingMonth(UUID contractId, LocalDate billingMonth);
}
