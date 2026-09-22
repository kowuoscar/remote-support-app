package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Country;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * An Agent and its login, created together (create-agent-with-login ticket). {@code username} and
 * {@code password} follow exactly the same rules as {@link TesterCreateRequest}'s. {@code
 * password} additionally carries the shared {@link PasswordPolicy} minimum
 * (password-minimum-length ticket); a violation returns the same ordinary Bean Validation {@code
 * 400} this endpoint already produces for a blank password — no new {@code code}.
 */
public record AgentCreateRequest(
    @NotBlank String name,
    @NotNull Country country,
    @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal salaryAmount,
    @NotBlank String username,
    @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, message = PasswordPolicy.TOO_SHORT_MESSAGE)
        String password) {

  /** Never echo the password — e.g. in a validation-failure log line that prints the request. */
  @Override
  public String toString() {
    return "AgentCreateRequest[name=%s, country=%s, salaryAmount=%s, username=%s]"
        .formatted(name, country, salaryAmount, username);
  }
}
