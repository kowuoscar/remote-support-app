package com.remotesupport.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of a {@link ClientInvoice} (edit-client-invoice-lines spec, "The model"). {@code amount}
 * is what the Agent billed; {@code computedAmount} is what the computation said for the line. A
 * line is <em>edited</em> when the two differ. A {@code POSTPAID_SIM} line carries {@code simCard},
 * a {@code FEE} line carries {@code fee}, a {@code BASE_AMOUNT} line (backfill only) neither; the
 * table's check enforces it.
 */
@Entity
@Table(name = "client_invoice_lines")
@Getter
@Setter
@NoArgsConstructor
public class ClientInvoiceLine {

  @Id private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "client_invoice_id", nullable = false)
  private ClientInvoice clientInvoice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ClientInvoiceLineKind kind;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sim_card_id")
  private SimCard simCard;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "fee_id")
  private Fee fee;

  @Column(name = "computed_amount", nullable = false)
  private BigDecimal computedAmount;

  @Column(nullable = false)
  private BigDecimal amount;

  /** Set by the Agent's last edit of this line; {@code null} if never edited. */
  @Column(name = "edited_at")
  private Instant editedAt;

  /** The user who made that last edit; {@code null} if never edited. */
  @Column(name = "edited_by")
  private UUID editedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
