package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Smartphone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmartphoneRepository extends JpaRepository<Smartphone, UUID> {

  List<Smartphone> findByContractIdOrderByCreatedAtAsc(UUID contractId);

  Optional<Smartphone> findByIdAndContractId(UUID id, UUID contractId);

  /** One Agent's own Stock (agent-stock ticket), tenant-scoped. */
  List<Smartphone> findByTenantIdAndHoldingAgentIdOrderByCreatedAtAsc(UUID tenantId, UUID holdingAgentId);

  /** Every Agent's Stock in the tenant (the Manager's own, unfiltered read). */
  List<Smartphone> findByTenantIdAndHoldingAgentIdIsNotNullOrderByCreatedAtAsc(UUID tenantId);
}
