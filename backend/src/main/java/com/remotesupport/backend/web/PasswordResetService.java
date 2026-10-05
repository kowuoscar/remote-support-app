package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.security.PasswordWrite;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Manager resets a Login's password (manager-resets-a-password spec, "The reset service"): the
 * target is resolved and guarded by the {@link AdministeredLoginLookup}, a generated password is
 * written, and one audit line follows. Returns the plaintext to the controller; reads no other part
 * of the target.
 */
@Service
public class PasswordResetService {

  private final AdministeredLoginLookup lookup;
  private final UserRepository userRepository;
  private final PasswordWrite passwordWrite;

  public PasswordResetService(
      AdministeredLoginLookup lookup, UserRepository userRepository, PasswordWrite passwordWrite) {
    this.lookup = lookup;
    this.userRepository = userRepository;
    this.passwordWrite = passwordWrite;
  }

  /** Failures are the lookup's: 404, 409 for a Login-less Agent, 403 from the guard. */
  @Transactional
  public String resetAgentPassword(UUID agentId, AuthenticatedPrincipal caller) {
    return reset(lookup.forAgent(agentId, caller), caller);
  }

  /** Failures are the lookup's: 404, 403 from the guard. */
  @Transactional
  public String resetTesterPassword(UUID clientId, UUID testerId, AuthenticatedPrincipal caller) {
    return reset(lookup.forTester(clientId, testerId, caller), caller);
  }

  private String reset(User login, AuthenticatedPrincipal caller) {
    String password = passwordWrite.setGeneratedPassword(login);
    userRepository.save(login);
    AuditLog.passwordChanged(login.getId(), caller.userId(), caller.tenantId());
    return password;
  }
}
