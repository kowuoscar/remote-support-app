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
 * The monthly invoice an {@link Agent} assembles for the Company Manager (spec.md Solution's
 * Agent Invoice entity; agent-standing-amounts-and-invoice-generation ticket, completed by
 * agent-invoice-submission-and-approval). One per Agent per {@code billingMonth} (first-of-month,
 * V14 migration's unique constraint) — same get-or-create shape {@code ClientInvoiceController}
 * established for Client Invoices, reused here by {@code AgentInvoiceController}.
 *
 * <p><b>Live while {@code DRAFT}, frozen from {@code SENT} onward</b> — the same shape {@link
 * ClientInvoice} established (ADR 0001), except all four line items freeze together (there is no
 * single "base amount" here). {@code AgentInvoiceController#send} populates the four {@code
 * snapshot*} columns once, in the same request as the {@code DRAFT -> SENT} transition; every read
 * from {@code SENT} onward serves them instead of recomputing. {@code snapshotSalary} and {@code
 * snapshotRolloutAdvanceNewAdvance} are the two a Manager may subsequently overwrite via {@code
 * AgentInvoiceByIdController#override} — see {@code AgentInvoiceService#override} and CONTEXT.md's "Agent Invoice"
 * entry for why only those two, and why the override never touches {@link AgentStandingAmount}.
 */
@Entity
@Table(name = "agent_invoices")
@Getter
@Setter
@NoArgsConstructor
public class AgentInvoice {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(optional = false)
  @JoinColumn(name = "agent_id", nullable = false)
  private Agent agent;

  @Column(name = "billing_month", nullable = false)
  private LocalDate billingMonth;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AgentInvoiceStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Currency currency;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Set once, at {@code DRAFT -> SENT}; {@code null} while still DRAFT. */
  @Column(name = "sent_at")
  private Instant sentAt;

  /** Set once, at {@code SENT -> APPROVED}; {@code null} before then. */
  @Column(name = "approved_at")
  private Instant approvedAt;

  /** Set once, at {@code APPROVED -> PAID}; {@code null} before then. */
  @Column(name = "paid_at")
  private Instant paidAt;

  /**
   * The Local Support Fees line frozen at send time; {@code null} while still {@code DRAFT}
   * (computed live instead — see the class Javadoc). Never overridable (only Salary and the new
   * advance are, per {@code AgentInvoiceService#override}).
   */
  @Column(name = "snapshot_local_support_fees")
  private BigDecimal snapshotLocalSupportFees;

  /** The Salary line frozen at send time; a Manager's override (class Javadoc) overwrites this directly. */
  @Column(name = "snapshot_salary")
  private BigDecimal snapshotSalary;

  /**
   * The Rollout Advance repayment line frozen at send time. Deliberately never overridable — see
   * CONTEXT.md's "Agent Invoice" entry: it settles an amount already fixed the month before, not
   * a decision the Manager is making now.
   */
  @Column(name = "snapshot_rollout_advance_repayment")
  private BigDecimal snapshotRolloutAdvanceRepayment;

  /** The Rollout Advance new-advance line frozen at send time; a Manager's override overwrites this directly. */
  @Column(name = "snapshot_rollout_advance_new_advance")
  private BigDecimal snapshotRolloutAdvanceNewAdvance;
}
