package com.remotesupport.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The {@code 201} body of the two Agent Login creation routes: {@link AgentResponse}'s fields
 * flattened, plus the generated {@code password}. The password is shown once; this record is never
 * used by a list endpoint, and {@code toString} redacts it.
 */
public record AgentCreatedResponse(
    UUID id,
    String name,
    String country,
    String currency,
    BigDecimal salaryAmount,
    long contractCount,
    String loginUsername,
    String password) {

  public static AgentCreatedResponse of(AgentResponse agent, String generatedPassword) {
    return new AgentCreatedResponse(
        agent.id(),
        agent.name(),
        agent.country(),
        agent.currency(),
        agent.salaryAmount(),
        agent.contractCount(),
        agent.loginUsername(),
        generatedPassword);
  }

  @Override
  public String toString() {
    return "AgentCreatedResponse[id=%s, name=%s, loginUsername=%s, password=%s]"
        .formatted(id, name, loginUsername, "[redacted]");
  }
}
