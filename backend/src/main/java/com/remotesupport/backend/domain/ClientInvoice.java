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
 * ClientInvoiceService#send} stores every line it shows as {@link ClientInvoiceLine} rows and sets
 * {@code linesStored} (ADR 0004). From then on ({@code SENT} and {@code APPROVED}) every read
 * serves those rows, never a fresh computation.
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
   * Legacy: the base amount the old send froze. No longer written or read (ADR 0004 replaced it
   * with {@link ClientInvoiceLine} rows); kept with its data, with {@link ClientInvoiceFeeSnapshot}.
   */
  @Column(name = "snapshot_base_amount")
  private BigDecimal snapshotBaseAmount;

  /**
   * Whether this invoice's lines are stored as {@link ClientInvoiceLine} rows rather than computed
   * (edit-client-invoice-lines spec, "The model"). {@code false} for a draft never sent.
   */
  @Column(name = "lines_stored", nullable = false)
  private boolean linesStored;

  /**
   * When a Manager last sent this invoice back to draft; {@code null} if never. Stays on the row
   * through the resend and is overwritten by the next send-back. A {@code DRAFT} carrying it is
   * "sent back" (send-a-client-invoice-back spec).
   */
  @Column(name = "sent_back_at")
  private Instant sentBackAt;

  /** The Manager's reason for the latest send-back; a note to the Agent, never shown to a Tester. */
  @Column(name = "sent_back_reason", length = 1000)
  private String sentBackReason;
}
