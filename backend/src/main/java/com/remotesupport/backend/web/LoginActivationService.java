package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.LoginActivationResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Manager switches a Login off or on (deactivate-a-login spec, "Endpoints"): the target is
 * resolved and guarded by the {@link AdministeredLoginLookup}, {@code deactivated_at} is set or
 * cleared, and one audit line follows a real change only. Both operations are idempotent: repeating
 * one returns the true state and leaves the original timestamp and the log untouched.
 */
@Service
public class LoginActivationService {

  private final AdministeredLoginLookup lookup;
  private final UserRepository userRepository;

  public LoginActivationService(AdministeredLoginLookup lookup, UserRepository userRepository) {
    this.lookup = lookup;
    this.userRepository = userRepository;
  }

  /** Failures are the lookup's: 404, 409 for a Login-less Agent, 403 from the guard. */
  @Transactional
  public LoginActivationResponse deactivateAgentLogin(
      UUID agentId, AuthenticatedPrincipal caller) {
    return deactivate(lookup.forAgent(agentId, caller), caller);
  }

  /** Failures are the lookup's: 404, 409 for a Login-less Agent, 403 from the guard. */
  @Transactional
  public LoginActivationResponse reactivateAgentLogin(
      UUID agentId, AuthenticatedPrincipal caller) {
    return reactivate(lookup.forAgent(agentId, caller), caller);
  }

  private LoginActivationResponse deactivate(User login, AuthenticatedPrincipal caller) {
    if (!login.isDeactivated()) {
      login.deactivate(Instant.now());
      userRepository.save(login);
      AuditLog.loginDeactivated(login.getId(), caller.userId(), caller.tenantId());
    }
    return new LoginActivationResponse(login.getDeactivatedAt());
  }

  private LoginActivationResponse reactivate(User login, AuthenticatedPrincipal caller) {
    if (login.isDeactivated()) {
      login.reactivate();
      userRepository.save(login);
      AuditLog.loginReactivated(login.getId(), caller.userId(), caller.tenantId());
    }
    return new LoginActivationResponse(login.getDeactivatedAt());
  }
}
