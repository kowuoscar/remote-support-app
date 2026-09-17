package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Country;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * An Agent and its login, created together (create-agent-with-login ticket). {@code username} and
 * {@code password} follow exactly the same rules as {@link TesterCreateRequest}'s.
 */
public record AgentCreateRequest(
    @NotBlank String name,
    @NotNull Country country,
    @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal salaryAmount,
    @NotBlank String username,
    @NotBlank String password) {

  /** Never echo the password — e.g. in a validation-failure log line that prints the request. */
  @Override
  public String toString() {
    return "AgentCreateRequest[name=%s, country=%s, salaryAmount=%s, username=%s]"
        .formatted(name, country, salaryAmount, username);
  }
}
