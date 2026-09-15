package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Contract;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

  List<Contract> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

  List<Contract> findByTenantIdAndAgentIdOrderByCreatedAtAsc(UUID tenantId, UUID agentId);

  List<Contract> findByTenantIdAndClientIdOrderByCreatedAtAsc(UUID tenantId, UUID clientId);

  Optional<Contract> findByIdAndTenantId(UUID id, UUID tenantId);

  long countByClientId(UUID clientId);

  long countByAgentId(UUID agentId);
}
