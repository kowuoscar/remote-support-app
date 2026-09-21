package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.Role;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.web.AgentLoginConflictException.Reason;
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

  // V1's Tenant-scoped users unique constraint, V55's global unique index and V16's
  // one-login-per-Agent index, as Postgres names them in a violation — how a conflict caught at
  // flush time is told apart. A caller must not be able to tell which of the two username
  // constraints fired, so both map to the same USERNAME_TAKEN reason
  // (globally-unique-usernames spec.md "One rule, five creation paths").
  private static final String TENANT_USERNAME_CONSTRAINT = "uq_users_tenant_username";
  private static final String GLOBAL_USERNAME_CONSTRAINT = "uq_users_username_global";
  private static final String ONE_LOGIN_PER_AGENT_INDEX = "uq_users_one_login_per_agent";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public AgentLoginService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Throws {@link AgentLoginConflictException} (409) when the Agent already has a login, or the
   * username is already taken anywhere in the deployment — the global, case- and trim-insensitive
   * check (globally-unique-usernames spec.md "One rule, five creation paths"), not merely the
   * Agent's own tenant. For a caller with nothing written yet (giving an existing Agent its
   * login), so the common conflicts answer cleanly without a failed insert.
   */
  public void requireLoginCreatable(Agent agent, String username) {
    if (userRepository.existsByAgentId(agent.getId())) {
      throw agentAlreadyHasLogin(agent);
    }
    if (userRepository.existsByUsernameNormalized(username)) {
      throw usernameTaken(username);
    }
  }

  /**
   * Writes the login and flushes, so a conflict surfaces here, inside the caller's transaction:
   * V16's one-login-per-Agent index and the {@code users} unique constraint are what reject it,
   * mapped to an {@link AgentLoginConflictException} (409) naming which one. The exception rolls
   * back everything the caller wrote before this call.
   */
  public User create(Agent agent, String username, String password, UUID actorUserId) {
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
      throw toConflict(e, agent, username);
    }

    AuditLog.agentLoginCreated(agent.getId(), user.getId(), actorUserId, agent.getTenant().getId());
    return user;
  }

  private static RuntimeException toConflict(
      DataIntegrityViolationException e, Agent agent, String username) {
    String detail = String.valueOf(e.getMostSpecificCause().getMessage());
    if (detail.contains(TENANT_USERNAME_CONSTRAINT) || detail.contains(GLOBAL_USERNAME_CONSTRAINT)) {
      return usernameTaken(username);
    }
    if (detail.contains(ONE_LOGIN_PER_AGENT_INDEX)) {
      return agentAlreadyHasLogin(agent);
    }
    return e;
  }

  private static AgentLoginConflictException usernameTaken(String username) {
    return new AgentLoginConflictException(
        Reason.USERNAME_TAKEN, "Username " + username + " is already in use");
  }

  private static AgentLoginConflictException agentAlreadyHasLogin(Agent agent) {
    return new AgentLoginConflictException(
        Reason.AGENT_ALREADY_HAS_LOGIN, "Agent " + agent.getId() + " already has a login");
  }
}
