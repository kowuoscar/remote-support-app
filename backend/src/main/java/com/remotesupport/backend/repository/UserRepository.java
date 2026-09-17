package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Users are looked up by username for authentication, and by id for token re-validation. */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByUsername(String username);

  boolean existsByTenantIdAndUsername(UUID tenantId, String username);

  /** The login linked to an Agent — at most one (V16's unique index). */
  Optional<User> findByAgentId(UUID agentId);

  boolean existsByAgentId(UUID agentId);
}
