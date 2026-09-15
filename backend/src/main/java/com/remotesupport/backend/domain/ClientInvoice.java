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
 * The monthly statement an Agent assembles for one {@link Contract} (spec.md Solution's Client
 * Invoice entity; client-invoice-generation ticket). One per Contract per {@code billingMonth}
 * (first-of-month, V11 migration's unique constraint) — see {@link
 * com.remotesupport.backend.web.ClientInvoiceController} for the "get or create the current
 * month's draft on first access" mechanic.
 *
 * <p><b>Live while {@code DRAFT}, frozen from {@code SENT} onward.</b> While {@code DRAFT}, the
 * base amount and Fee lines are still computed live from {@link SimCard}/{@link Fee} on every
 * read (client-invoice-generation ticket) — the Agent is still assembling it, so there is nothing
 * to freeze yet. The moment an Agent sends it ({@code DRAFT -> SENT}), {@code
 * ClientInvoiceController#send} snapshots both: {@code snapshotBaseAmount} here, and the Fee-line
 * membership into {@link ClientInvoiceFeeSnapshot} rows. From then on ({@code SENT} and {@code
 * APPROVED}) every read serves the snapshot, never a fresh computation.
 *
 * <p>This is a deliberate correctness/audit decision, not an oversight of the live-computation
 * design the previous ticket chose: a live-computed {@code SENT}/{@code APPROVED} invoice would
 * silently change its own total if an Agent logged a new Fee against the same Contract/month
 * afterwards — the Manager would be approving different numbers than the Agent actually sent, and
 * the Client would see a total that moves after the fact, on what both sides treat as a final
 * statement. See CONTEXT.md's "Client Invoice" entry and the ADR this ticket recorded for the
 * full reasoning; {@code Fee} having no update/delete path anywhere in this codebase
 * (fee-logging-and-provisioning ticket) is exactly why freezing membership — not a copy of every
 * Fee column — is enough (see {@link ClientInvoiceFeeSnapshot}'s Javadoc).
 */
@Entity
@Table(name = "client_invoices")
@Getter
@Setter
@NoArgsConstructor
public class ClientInvoice {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(optional = false)
  @JoinColumn(name = "contract_id", nullable = false)
  private Contract contract;

  @Column(name = "billing_month", nullable = false)
  private LocalDate billingMonth;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ClientInvoiceStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Currency currency;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Set once, at {@code DRAFT -> SENT} (see the class Javadoc); {@code null} while still DRAFT. */
  @Column(name = "sent_at")
  private Instant sentAt;

  /** Set once, at {@code SENT -> APPROVED}; {@code null} before then. */
  @Column(name = "approved_at")
  private Instant approvedAt;

  /**
   * The base amount frozen at send time; {@code null} while still {@code DRAFT} (computed live
   * instead — see the class Javadoc). Paired with {@link ClientInvoiceFeeSnapshot} for the frozen
   * Fee lines; together they are the whole snapshot.
   */
  @Column(name = "snapshot_base_amount")
  private BigDecimal snapshotBaseAmount;
}
