package com.remotesupport.backend.security;

import java.util.UUID;
import org.springframework.security.authentication.DisabledException;

/**
 * The right password was presented for a deactivated Login. Carries the ids the sign-in refusal
 * logs, since a {@link DisabledException} alone names nobody. Never thrown for a wrong password.
 */
public class LoginDeactivatedException extends DisabledException {

  private final UUID userId;
  private final UUID tenantId;

  public LoginDeactivatedException(UUID userId, UUID tenantId) {
    super("Login is deactivated");
    this.userId = userId;
    this.tenantId = tenantId;
  }

  public UUID userId() {
    return userId;
  }

  public UUID tenantId() {
    return tenantId;
  }
}
