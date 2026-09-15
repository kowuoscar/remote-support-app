package com.remotesupport.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One {@link Fee} pinned into a {@link ClientInvoice}'s frozen total the moment it was sent
 * (client-invoice-submission-and-visibility ticket; CONTEXT.md "Client Invoice" snapshot-on-send
 * decision). A row here means "this Fee counted toward this Client Invoice's total when it was
 * sent" — nothing more.
 *
 * <p>Deliberately a thin membership row, not a copy of {@link Fee}'s own columns: {@code Fee} has
 * no update or delete endpoint anywhere in this codebase (fee-logging-and-provisioning ticket), so
 * a {@code Fee}'s own amount/description/etc. can never change out from under an invoice that
 * references it — the only thing that can change after sending is the *set* of Fees for this
 * Contract/month (an Agent logging a new one later), and that's exactly what this table pins.
 * Duplicating every {@code Fee} column here would just be a second, driftable copy of data {@code
 * Fee} already owns permanently.
 */
@Entity
@Table(name = "client_invoice_fee_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class ClientInvoiceFeeSnapshot {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(optional = false)
  @JoinColumn(name = "client_invoice_id", nullable = false)
  private ClientInvoice clientInvoice;

  @ManyToOne(optional = false)
  @JoinColumn(name = "fee_id", nullable = false)
  private Fee fee;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
