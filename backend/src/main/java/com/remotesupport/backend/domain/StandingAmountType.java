package com.remotesupport.backend.domain;

/**
 * The two standing per-Agent amounts a Manager sets (spec.md Solution: "a standing monthly
 * salary, and a standing Rollout Advance amount, both Manager-set"; CONTEXT.md "Agent"/"Rollout
 * Advance"). Distinguishes the two histories kept in {@link AgentStandingAmount}, which are
 * otherwise identical in shape (amount + effective month + who set it + when).
 */
public enum StandingAmountType {
  SALARY,
  ROLLOUT_ADVANCE
}
