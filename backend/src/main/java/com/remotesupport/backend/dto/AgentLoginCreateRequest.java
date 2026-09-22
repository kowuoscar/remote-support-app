package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A login for an existing Agent that has none (create-login-for-existing-agent ticket). {@code
 * username} and {@code password} follow exactly the same rules as {@link TesterCreateRequest}'s:
 * {@code password} carries the shared {@link PasswordPolicy} minimum (password-minimum-length
 * ticket, F1) — this is a fourth boundary that writes a password, and the spec's Goal ("that same
 * rule holds everywhere a password is set, not only here") does not stop at three DTOs.
 */
public record AgentLoginCreateRequest(
    @NotBlank String username,
    @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, message = PasswordPolicy.TOO_SHORT_MESSAGE)
        String password) {

  /** Never echo the password — e.g. in a validation-failure log line that prints the request. */
  @Override
  public String toString() {
    return "AgentLoginCreateRequest[username=%s]".formatted(username);
  }
}
