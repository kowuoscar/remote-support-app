package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.SimCard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimCardRepository extends JpaRepository<SimCard, UUID> {

  List<SimCard> findByContractIdOrderByCreatedAtAsc(UUID contractId);

  Optional<SimCard> findByIdAndContractId(UUID id, UUID contractId);

  /**
   * Every SIM Card currently Installed in {@code smartphoneId} (sim-installed-in-smartphone
   * ticket): the two-SIM check reads this to see what a Smartphone already holds, and retiring a
   * Smartphone reads it to know which links to clear.
   */
  List<SimCard> findByInstalledInSmartphoneId(UUID smartphoneId);

  /** One Agent's own Stock (agent-stock ticket), tenant-scoped. */
  List<SimCard> findByTenantIdAndHoldingAgentIdOrderByCreatedAtAsc(UUID tenantId, UUID holdingAgentId);

  /** Every Agent's Stock in the tenant (the Manager's own, unfiltered read). */
  List<SimCard> findByTenantIdAndHoldingAgentIdIsNotNullOrderByCreatedAtAsc(UUID tenantId);
}
