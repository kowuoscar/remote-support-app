package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.AgentStandingAmount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One recorded standing-amount change, confirming what was scheduled and from when. */
public record AgentStandingAmountResponse(
    UUID id, UUID agentId, String amountType, BigDecimal amount, LocalDate effectiveMonth) {

  public static AgentStandingAmountResponse of(AgentStandingAmount row) {
    return new AgentStandingAmountResponse(
        row.getId(),
        row.getAgent().getId(),
        row.getAmountType().name(),
        row.getAmount(),
        row.getEffectiveMonth());
  }
}
