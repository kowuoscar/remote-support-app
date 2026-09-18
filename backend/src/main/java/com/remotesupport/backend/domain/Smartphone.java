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
 * A Smartphone provisioned under one {@link Contract}'s Fleet (spec.md Solution). Fleet has no
 * separate table — Smartphone references its owning Contract directly.
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

  @ManyToOne(optional = false)
  @JoinColumn(name = "contract_id", nullable = false)
  private Contract contract;

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
