package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Agent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code loginUsername} is the email the Agent signs in with, or {@code null} for an Agent created
 * before every Agent got a login at creation (agent-login-on-creation spec, "Agent read model").
 * {@code loginDeactivatedAt} is when that Login was deactivated, {@code null} while it is active or
 * when there is no Login (deactivate-a-login spec, "Read models").
 */
public record AgentResponse(
    UUID id,
    String name,
    String country,
    String currency,
    BigDecimal salaryAmount,
    long contractCount,
    String loginUsername,
    Instant loginDeactivatedAt) {

  public static AgentResponse of(
      Agent agent, long contractCount, String loginUsername, Instant loginDeactivatedAt) {
    return new AgentResponse(
        agent.getId(),
        agent.getName(),
        agent.getCountry().name(),
        agent.getCurrency().name(),
        agent.getSalaryAmount(),
        contractCount,
        loginUsername,
        loginDeactivatedAt);
  }
}
