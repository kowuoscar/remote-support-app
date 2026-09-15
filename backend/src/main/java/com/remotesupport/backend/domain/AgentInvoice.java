package com.remotesupport.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The monthly invoice an {@link Agent} assembles for the Company Manager (spec.md Solution's
 * Agent Invoice entity; agent-standing-amounts-and-invoice-generation ticket). One per Agent per
 * {@code billingMonth} (first-of-month, V14 migration's unique constraint) — same get-or-create
 * shape {@code ClientInvoiceController} established for Client Invoices, reused here by {@code
 * AgentInvoiceController}.
 *
 * <p>Unlike {@link ClientInvoice}, this entity carries no snapshot fields yet: every line item
 * (Local Support Fees, Salary, the two Rollout Advance lines) is computed live on every read while
 * {@code DRAFT} — the only status this ticket ever writes. The next ticket
 * (agent-invoice-submission-and-approval) adds the send/approve/paid transitions and, following
 * {@link ClientInvoice}'s precedent, will need to freeze these numbers at send time; that snapshot
 * mechanism is out of scope here.
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
}
