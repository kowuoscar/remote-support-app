package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A login for an existing Agent that has none (create-login-for-existing-agent ticket). {@code
 * username} and {@code password} follow exactly the same rules as {@link TesterCreateRequest}'s.
 */
public record AgentLoginCreateRequest(@NotBlank String username, @NotBlank String password) {

  /** Never echo the password — e.g. in a validation-failure log line that prints the request. */
  @Override
  public String toString() {
    return "AgentLoginCreateRequest[username=%s]".formatted(username);
  }
}
