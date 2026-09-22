package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.web.ChangePasswordRefusedException.Reason;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes the caller's own password (change-own-password-endpoint ticket) — the first write
 * acting on the caller rather than some other entity, resolved from {@code
 * principal.userId()} and nothing supplied by the request (spec.md Solution: "The endpoint is the
 * first write acting on the caller"). Deliberately does not reach into {@link AgentLoginService}
 * or {@link TesterLoginService}, and neither of those two gains an update operation: the epic's
 * manager-resets-a-password ticket owns unifying the three password-write call sites into one,
 * not this one (spec.md Solution: "The service, and what it deliberately does not do").
 */
@Service
public class ChangePasswordService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public ChangePasswordService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Loads the caller's own {@link User} by {@code principal.userId()}, verifies {@code
   * currentPassword} against its stored hash with {@link PasswordEncoder#matches} directly —
   * never a round-trip through {@code AuthenticationManager}, which would mint a fresh {@code
   * Authentication} and make a password change indistinguishable from a sign-in in the logs
   * (spec.md Solution: "Verifying the current password") — encodes and sets the new hash, and
   * writes one {@code PASSWORD_CHANGED} audit line. Refuses with {@link
   * ChangePasswordRefusedException} (400, never 401/403 — spec.md Constraints) for a wrong current
   * password or a new password identical to the current one; a failed verification writes no
   * audit line (spec.md Observability).
   */
  @Transactional
  public void changeOwnPassword(
      AuthenticatedPrincipal principal, String currentPassword, String newPassword) {
    User user =
        userRepository
            .findById(principal.userId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Authenticated user " + principal.userId() + " has no User row"));

    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new ChangePasswordRefusedException(
          Reason.WRONG_CURRENT_PASSWORD, "The current password is incorrect");
    }
    if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
      throw new ChangePasswordRefusedException(
          Reason.PASSWORD_UNCHANGED, "The new password must be different from the current one");
    }

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);

    AuditLog.passwordChanged(user.getId(), principal.userId(), principal.tenantId());
  }
}
