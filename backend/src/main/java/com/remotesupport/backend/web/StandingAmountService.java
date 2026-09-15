package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.AgentStandingAmount;
import com.remotesupport.backend.domain.StandingAmountType;
import com.remotesupport.backend.repository.AgentStandingAmountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves and records an Agent's standing salary/Rollout Advance history (see {@link
 * AgentStandingAmount}'s Javadoc for the versioning model this implements).
 */
@Service
public class StandingAmountService {

  private final AgentStandingAmountRepository repository;

  public StandingAmountService(AgentStandingAmountRepository repository) {
    this.repository = repository;
  }

  /**
   * The amount in effect for {@code month}: the most recently set row with {@code effectiveMonth
   * <= month}, or {@link BigDecimal#ZERO} if none exists yet — always true for a
   * not-yet-set-by-a-Manager Rollout Advance (CONTEXT.md "Rollout Advance"), and, before this
   * ticket's backfill, would also have been true for a stray Agent with no SALARY row at all
   * (never the case in practice — see {@link AgentStandingAmount}'s Javadoc).
   */
  public BigDecimal resolve(UUID agentId, StandingAmountType amountType, LocalDate month) {
    return repository
        .findFirstByAgentIdAndAmountTypeAndEffectiveMonthLessThanEqualOrderByEffectiveMonthDescSetAtDesc(
            agentId, amountType, month)
        .map(AgentStandingAmount::getAmount)
        .orElse(BigDecimal.ZERO);
  }

  /** Writes a new standing-amount history row — never updates or deletes an existing one. */
  public AgentStandingAmount record(
      Agent agent, StandingAmountType amountType, BigDecimal amount, LocalDate effectiveMonth, UUID actorUserId) {
    AgentStandingAmount row = new AgentStandingAmount();
    row.setId(UUID.randomUUID());
    row.setTenant(agent.getTenant());
    row.setAgent(agent);
    row.setAmountType(amountType);
    row.setAmount(amount);
    row.setEffectiveMonth(effectiveMonth);
    row.setSetByUserId(actorUserId);
    row.setSetAt(Instant.now());
    return repository.save(row);
  }
}
