package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.ClientInvoiceLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientInvoiceLineRepository extends JpaRepository<ClientInvoiceLine, UUID> {

  List<ClientInvoiceLine> findByClientInvoiceId(UUID clientInvoiceId);
}
