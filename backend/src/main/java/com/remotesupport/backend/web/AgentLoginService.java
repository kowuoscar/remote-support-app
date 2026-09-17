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
   * Throws {@link ConflictException} (409) when the Agent already has a login, or the username is
   * already in use in the Agent's tenant. Both checks run first for a clean 409; V16's
   * one-login-per-Agent index and the {@code users} unique constraint back them up against a
   * concurrent request.
   */
  public User create(Agent agent, String username, String password, UUID actorUserId) {
    UUID tenantId = agent.getTenant().getId();
    if (userRepository.existsByAgentId(agent.getId())) {
      throw new ConflictException("Agent " + agent.getId() + " already has a login");
    }
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
      throw new ConflictException(
          "Username " + username + " is already in use, or Agent " + agent.getId() + " already has a login");
    }

    AuditLog.agentLoginCreated(agent.getId(), user.getId(), actorUserId, tenantId);
    return user;
  }
}
