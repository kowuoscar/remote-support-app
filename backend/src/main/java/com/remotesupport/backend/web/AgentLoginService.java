package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.Role;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Creates an Agent's login: an {@code AGENT}-role {@link User} linked to that {@link Agent}
 * (agent-login-on-creation spec). The one place this happens, so creating an Agent with its login
 * and giving an existing Agent a login can never diverge. Joins the caller's transaction: a
 * conflict here rolls back whatever the caller already wrote (e.g. the Agent itself).
 */
@Service
public class AgentLoginService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public AgentLoginService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Throws {@link ConflictException} (409) when the username is already in use in the tenant. A
   * caller about to write other rows alongside the login calls this before writing anything, so
   * the common conflict never reaches a rollback at all.
   */
  public void requireUsernameAvailable(UUID tenantId, String username) {
    if (userRepository.existsByTenantIdAndUsername(tenantId, username)) {
      throw new ConflictException("Username " + username + " is already in use");
    }
  }

  /**
   * Throws {@link ConflictException} (409) when the username is already in use in the Agent's
   * tenant. The check runs first for a clean 409; the {@code users} unique constraint (and V16's
   * one-login-per-Agent index) back it up against a concurrent request.
   */
  public User create(Agent agent, String username, String password, UUID actorUserId) {
    UUID tenantId = agent.getTenant().getId();
    requireUsernameAvailable(tenantId, username);

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setTenant(agent.getTenant());
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(Role.AGENT);
    user.setAgent(agent);
    user.setCreatedAt(Instant.now());
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("Username " + username + " is already in use");
    }

    AuditLog.agentLoginCreated(agent.getId(), user.getId(), actorUserId, tenantId);
    return user;
  }
}
