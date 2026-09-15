package com.remotesupport.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One entry in an {@link Agent}'s standing-amount history: a Manager setting the standing salary
 * or standing Rollout Advance to {@code amount}, effective starting {@code effectiveMonth}
 * (spec.md user stories 5-6: "taking effect from the next invoice cycle";
 * agent-standing-amounts-and-invoice-generation ticket).
 *
 * <p>Modeled as an append-only history table, not a mutable column on {@link Agent}, because
 * "what amount was in effect for month X" must be answerable for any month — the current invoice
 * being built, and (implicitly, for the next ticket's history views) any past one — and a change
 * must never retroactively affect a month already in progress. Resolving "the amount in effect
 * for month X" = the most recent row (by {@code effectiveMonth}, then {@code setAt} as a
 * same-month tiebreaker when a Manager corrects a not-yet-effective change) with {@code
 * effectiveMonth <= X}. See {@code AgentInvoiceController}/{@code StandingAmountService}.
 *
 * <p>{@link StandingAmountType#SALARY} always has at least one row for a given Agent — one is
 * written the moment the Agent is created ({@code AgentController#create}), using the salary the
 * Manager supplied then, so resolution never needs a special-cased fallback to a separate column.
 * {@link StandingAmountType#ROLLOUT_ADVANCE} deliberately has none until a Manager sets one for
 * the first time — resolving it before then returns 0/absent (an Agent's Rollout Advance is opt-in
 * cash-flow support, not something every Agent has from day one).
 */
@Entity
@Table(name = "agent_standing_amounts")
@Getter
@Setter
@NoArgsConstructor
public class AgentStandingAmount {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(optional = false)
  @JoinColumn(name = "agent_id", nullable = false)
  private Agent agent;

  @Enumerated(EnumType.STRING)
  @Column(name = "amount_type", nullable = false)
  private StandingAmountType amountType;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(name = "effective_month", nullable = false)
  private LocalDate effectiveMonth;

  @Column(name = "set_by_user_id", nullable = false)
  private UUID setByUserId;

  @Column(name = "set_at", nullable = false, updatable = false)
  private Instant setAt;
}
