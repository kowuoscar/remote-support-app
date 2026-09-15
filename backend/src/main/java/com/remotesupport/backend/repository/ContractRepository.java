package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Contract;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

  List<Contract> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

  long countByClientId(UUID clientId);

  long countByAgentId(UUID agentId);
}
