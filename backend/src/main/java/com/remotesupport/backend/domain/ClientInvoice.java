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
 * The monthly statement an Agent assembles for one {@link Contract} (spec.md Solution's Client
 * Invoice entity; client-invoice-generation ticket). One per Contract per {@code billingMonth}
 * (first-of-month, V11 migration's unique constraint) — see {@link
 * com.remotesupport.backend.web.ClientInvoiceController} for the "get or create the current
 * month's draft on first access" mechanic.
 *
 * <p>Deliberately does <b>not</b> carry a base amount or Fee-line total column: spec.md is
 * explicit that the base amount is computed "at the time of viewing", so it — and the Fee lines —
 * are always computed live from {@link SimCard}/{@link Fee} on every read, never snapshotted
 * here. {@code currency} is copied from the Contract's own currency at creation time, the same
 * "copied, not re-derived on read" pattern {@link Contract#currency} and {@link Fee#currency}
 * already use.
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
}
