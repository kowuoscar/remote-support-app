package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Agent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, UUID> {

  List<Agent> findByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<Agent> findByIdAndTenantId(UUID id, UUID tenantId);
}
