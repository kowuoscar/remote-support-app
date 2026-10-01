package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.security.LoginAdministrationGuard;
import com.remotesupport.backend.security.PasswordWrite;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Manager resets a Login's password (manager-resets-a-password spec, "The reset service"): the
 * target is resolved within the caller's Tenant, the {@link LoginAdministrationGuard} is asked, a
 * generated password is written, and one audit line follows. Returns the plaintext to the
 * controller; reads no other part of the target.
 */
@Service
public class PasswordResetService {

  private final AgentRepository agentRepository;
  private final UserRepository userRepository;
  private final LoginAdministrationGuard guard;
  private final PasswordWrite passwordWrite;

  public PasswordResetService(
      AgentRepository agentRepository,
      UserRepository userRepository,
      LoginAdministrationGuard guard,
      PasswordWrite passwordWrite) {
    this.agentRepository = agentRepository;
    this.userRepository = userRepository;
    this.guard = guard;
    this.passwordWrite = passwordWrite;
  }

  /**
   * 404 ({@link NotFoundException}) for an unknown or other-Tenant Agent, 409 ({@link
   * AgentHasNoLoginException}) for an Agent without a Login, 403 when the guard refuses.
   */
  @Transactional
  public String resetAgentPassword(UUID agentId, AuthenticatedPrincipal caller) {
    Agent agent =
        agentRepository
            .findByIdAndTenantId(agentId, caller.tenantId())
            .orElseThrow(() -> new NotFoundException("No agent with id " + agentId));
    User login =
        userRepository
            .findByAgentId(agent.getId())
            .orElseThrow(
                () -> new AgentHasNoLoginException("Agent " + agent.getId() + " has no login"));
    return reset(login, caller);
  }

  private String reset(User login, AuthenticatedPrincipal caller) {
    guard.requireCanAdminister(login, caller);
    String password = passwordWrite.setGeneratedPassword(login);
    userRepository.save(login);
    AuditLog.passwordChanged(login.getId(), caller.userId(), caller.tenantId());
    return password;
  }
}
