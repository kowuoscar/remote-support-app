package com.remotesupport.backend.dto;

import java.math.BigDecimal;

/**
 * The standing salary and standing Rollout Advance currently in effect for an Agent (i.e.
 * resolved for the calendar month in progress) — the Manager detail page's form defaults. A
 * change a Manager makes never shows up here until the month it takes effect actually arrives
 * (see {@code StandingAmountService#resolve}).
 */
public record AgentStandingAmountsResponse(BigDecimal salaryAmount, BigDecimal rolloutAdvanceAmount) {}
