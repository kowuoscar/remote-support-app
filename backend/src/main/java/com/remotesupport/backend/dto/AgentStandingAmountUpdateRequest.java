package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.StandingAmountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * A Manager updating an Agent's standing salary or standing Rollout Advance
 * (agent-standing-amounts-and-invoice-generation ticket). Carries no {@code effectiveMonth}: the
 * server always computes it as the month after the one in progress — a Manager can never
 * backdate a standing-amount change onto the current invoice (spec.md user stories 5-6).
 */
public record AgentStandingAmountUpdateRequest(
    @NotNull StandingAmountType amountType,
    @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal amount) {}
