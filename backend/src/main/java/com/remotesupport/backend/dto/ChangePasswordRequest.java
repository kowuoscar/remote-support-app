package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/me/password} (change-own-password-endpoint ticket). {@code
 * newPassword} additionally carries the shared {@link PasswordPolicy} minimum
 * (password-minimum-length ticket); a violation is given its own {@code PASSWORD_TOO_SHORT} code
 * by {@link com.remotesupport.backend.web.ChangePasswordController}, distinct from a wrong current
 * password, so the change-password dialog can point at the right field.
 */
public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, message = PasswordPolicy.TOO_SHORT_MESSAGE)
        String newPassword) {

  /** Never echo either password — e.g. in a validation-failure log line that prints the request. */
  @Override
  public String toString() {
    return "ChangePasswordRequest[currentPassword=<redacted>, newPassword=<redacted>]";
  }
}
