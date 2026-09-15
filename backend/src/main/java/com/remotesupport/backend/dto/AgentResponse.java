package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Agent;
import java.math.BigDecimal;
import java.util.UUID;

public record AgentResponse(
    UUID id,
    String name,
    String country,
    String currency,
    BigDecimal salaryAmount,
    long contractCount) {

  public static AgentResponse of(Agent agent, long contractCount) {
    return new AgentResponse(
        agent.getId(),
        agent.getName(),
        agent.getCountry().name(),
        agent.getCurrency().name(),
        agent.getSalaryAmount(),
        contractCount);
  }
}
