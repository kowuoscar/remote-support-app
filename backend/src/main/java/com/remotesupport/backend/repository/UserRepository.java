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

  /**
   * Whether any {@code users} row, in any Tenant, already holds this username once normalized
   * the same way as V55's {@code uq_users_username_global} index — case-insensitive, surrounding
   * whitespace ignored. The pre-insert convenience check every login-creation path runs ahead of
   * the index itself, which remains the authority (globally-unique-usernames spec.md "One rule,
   * five creation paths").
   */
  @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(TRIM(u.username)) = LOWER(TRIM(:username))")
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
