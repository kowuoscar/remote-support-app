package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The body of {@code POST /api/me/password} (change-own-password-endpoint ticket). The
 * 8-character minimum on {@code newPassword} is added by the password-minimum-length ticket, not
 * here — this ticket's own validation is only that both fields are supplied.
 */
public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {

  /** Never echo either password — e.g. in a validation-failure log line that prints the request. */
  @Override
  public String toString() {
    return "ChangePasswordRequest[currentPassword=<redacted>, newPassword=<redacted>]";
  }
}
