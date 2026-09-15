package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.ClientInvoiceFeeSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientInvoiceFeeSnapshotRepository extends JpaRepository<ClientInvoiceFeeSnapshot, UUID> {

  List<ClientInvoiceFeeSnapshot> findByClientInvoiceId(UUID clientInvoiceId);
}
