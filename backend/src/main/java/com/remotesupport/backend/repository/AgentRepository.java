package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Agent;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRepository extends JpaRepository<Agent, UUID> {

  List<Agent> findByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<Agent> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * The Agent's row under a write lock: what an Agent Invoice's first-ever send and a Client Invoice
   * edit both take, so they serialise even while no Agent Invoice row exists to lock (ADR 0004).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Agent a where a.id = :id")
  Optional<Agent> findByIdForUpdate(@Param("id") UUID id);
}
