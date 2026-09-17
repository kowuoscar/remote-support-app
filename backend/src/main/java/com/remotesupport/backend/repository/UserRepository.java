package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Users are looked up by username for authentication, and by id for token re-validation. */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByUsername(String username);

  boolean existsByTenantIdAndUsername(UUID tenantId, String username);

  boolean existsByAgentId(UUID agentId);

  /** Every Agent login in the tenant, in one query — at most one per Agent (V16's unique index). */
  @Query(
      "SELECT new com.remotesupport.backend.repository.AgentLogin(u.agent.id, u.username)"
          + " FROM User u WHERE u.tenant.id = :tenantId AND u.agent IS NOT NULL")
  List<AgentLogin> findAgentLoginsByTenantId(@Param("tenantId") UUID tenantId);
}
