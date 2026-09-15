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
 * A billable line item an Agent logs against a Contract, always tracing back to the {@link
 * Request} that caused it (spec.md Solution: "Fee ... always linked to the Request that caused
 * it"; CONTEXT.md "Fee"; fee-logging-and-provisioning ticket). {@code request} is non-nullable at
 * both the JPA and the database level (V9 migration's {@code NOT NULL} FK) — the traceability
 * invariant this ticket's AC requires ("there is no way to create an untraceable Fee") is enforced
 * by the schema itself, not merely by the service/controller layer, so no write path (present or
 * future) can create a bare Fee. {@code contract} is denormalized from {@code request.contract} —
 * every other Fleet-adjacent table in this schema (Smartphone, SIM Card, Request) already stores
 * its owning Contract directly rather than requiring a join to find it, and the next ticket
 * (client-invoice-generation) needs "every Fee logged against this Contract this month" to be a
 * plain {@code WHERE contract_id = ? AND billing_month = ?} query.
 *
 * <p>{@code feeType} mirrors the subset of {@link RequestType} that can carry a Fee — see {@link
 * FeeType}'s Javadoc for why a Reboot or a like-for-like SIM Swap can never become one.
 *
 * <p>{@code amount}/{@code currency}: currency is always copied from the Contract's own currency
 * at creation time (mirrors {@link Contract#currency}'s "copied, not re-derived on read" note) —
 * the Agent never picks a different one, so a Fee can never drift from the Contract it bills
 * against.
 *
 * <p>{@code billingMonth}: which invoicing month this Fee counts toward (client-invoice-generation
 * ticket: "every Fee logged against the Contract this month appears as its own line"). Stored as
 * an explicit first-of-month {@link LocalDate} rather than derived from {@code createdAt} at query
 * time, even though the two agree for every Fee this ticket's UI ever produces (there's no
 * backdating control yet): a stored column makes "sum this Contract's Fees for month X" a direct
 * equality filter with no date-truncation logic duplicated at every call site, and it's the column
 * that would absorb a future backdating feature (an Agent logging a Fee a few days into the month
 * but attributing it to the prior month's invoice) without a schema change — only a UI/validation
 * addition. Today it is always set to the month {@code createdAt} falls in.
 */
@Entity
@Table(name = "fees")
@Getter
@Setter
@NoArgsConstructor
public class Fee {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(optional = false)
  @JoinColumn(name = "contract_id", nullable = false)
  private Contract contract;

  @ManyToOne(optional = false)
  @JoinColumn(name = "request_id", nullable = false)
  private Request request;

  @Enumerated(EnumType.STRING)
  @Column(name = "fee_type", nullable = false)
  private FeeType feeType;

  @Column(nullable = false)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Currency currency;

  @Column private String description;

  @Column(name = "billing_month", nullable = false)
  private LocalDate billingMonth;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
