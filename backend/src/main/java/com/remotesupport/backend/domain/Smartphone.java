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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A Smartphone provisioned under one {@link Contract}'s Fleet, or held in one {@link Agent}'s
 * Stock (returns-and-agent-stock spec, Solution's Agent Stock; agent-stock ticket) — Fleet has no
 * separate table — Smartphone references its owning Contract directly. Exactly one of {@code
 * contract}/{@code holdingAgent} is set (V52 migration's own CHECK, mirroring {@link
 * com.remotesupport.backend.domain.ReturnedUnit}'s own exactly-one-of check): a unit in the Agent's
 * Stock belongs to no Contract (CONTEXT.md "Agent Stock": "a unit in exactly one place, a
 * Contract's Fleet or an Agent's Stock"). Set only by {@code ReturnCompletionEffect} (entering
 * Stock, a Kept in Stock Disposition) and, in a later ticket, by Stock fulfilment (leaving it).
 */
@Entity
@Table(name = "smartphones")
@Getter
@Setter
@NoArgsConstructor
public class Smartphone {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne
  @JoinColumn(name = "contract_id")
  private Contract contract;

  /** The Agent holding this Smartphone in their Stock, or {@code null} while it's on a Contract. */
  @ManyToOne
  @JoinColumn(name = "holding_agent_id")
  private Agent holdingAgent;

  @Column(nullable = false)
  private String model;

  // Optional (smartphone-owner-and-optional-serial ticket AC: "A Smartphone can be created
  // without a serial") -- the Agent (own Contract) or the Manager can set or change it later
  // from the Fleet page.
  @Column private String serial;

  // Client or company (spec.md Solution — Fleet model). Always COMPANY for a Smartphone reached
  // through a Provision or Replace Request; the Manager's add-Smartphone form asks, defaulting
  // to company. Replaces the removed free-text "assigned to".
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SmartphoneOwner owner;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SmartphoneStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
