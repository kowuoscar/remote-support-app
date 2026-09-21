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

  /**
   * Whether any {@link User} anywhere in the deployment already holds this username once both are
   * normalized case- and trim-insensitively — the same normalization V55's {@code
   * uq_users_username_global} index applies (globally-unique-usernames spec.md "Normalization,
   * the same three layers as {@code carrier-catalog}"). Used as a pre-check ahead of a write, the
   * database index remaining the authority either way.
   */
  @Query("SELECT COUNT(u) > 0 FROM User u WHERE lower(trim(u.username)) = lower(trim(:username))")
  boolean existsByUsernameNormalized(@Param("username") String username);

  boolean existsByAgentId(UUID agentId);

  /** An Agent's own login, if it has one (at most one, per V16's unique index) — demo-story-loader
   * needs the login's own user id to build that Agent's {@code AuthenticatedPrincipal}. */
  Optional<User> findByAgentId(UUID agentId);

  /** Every Agent login in the tenant, in one query — at most one per Agent (V16's unique index). */
  @Query(
      "SELECT new com.remotesupport.backend.repository.AgentLogin(u.agent.id, u.username)"
          + " FROM User u WHERE u.tenant.id = :tenantId AND u.agent IS NOT NULL")
  List<AgentLogin> findAgentLoginsByTenantId(@Param("tenantId") UUID tenantId);
}
