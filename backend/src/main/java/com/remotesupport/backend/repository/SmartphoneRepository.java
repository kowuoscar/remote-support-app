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

  /**
   * A single Stock unit, scoped to the Agent holding it (fulfil-from-stock ticket AC: "A Stock
   * unit of another Agent ... is refused") — mirrors {@link #findByIdAndContractId}'s own
   * ownership-scoped-lookup shape at the other end of a unit's life.
   */
  Optional<Smartphone> findByIdAndTenantIdAndHoldingAgentId(UUID id, UUID tenantId, UUID holdingAgentId);
}
