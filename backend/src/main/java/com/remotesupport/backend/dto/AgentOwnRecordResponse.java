package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Agent;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The calling Agent's own record with the standing salary and Rollout Advance in effect for the
 * current billing month ({@code GET /api/me/agent}).
 */
public record AgentOwnRecordResponse(
    UUID agentId,
    String name,
    String country,
    String currency,
    BigDecimal salaryAmount,
    BigDecimal rolloutAdvanceAmount) {

  public static AgentOwnRecordResponse of(
      Agent agent, BigDecimal salaryAmount, BigDecimal rolloutAdvanceAmount) {
    return new AgentOwnRecordResponse(
        agent.getId(),
        agent.getName(),
        agent.getCountry().name(),
        agent.getCurrency().name(),
        salaryAmount,
        rolloutAdvanceAmount);
  }
}
