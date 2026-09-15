package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.AgentInvoice;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentInvoiceRepository extends JpaRepository<AgentInvoice, UUID> {

  Optional<AgentInvoice> findByAgentIdAndBillingMonth(UUID agentId, LocalDate billingMonth);
}
