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

  @Column(nullable = false)
  private String serial;

  @Column(name = "assigned_to")
  private String assignedTo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SmartphoneStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
