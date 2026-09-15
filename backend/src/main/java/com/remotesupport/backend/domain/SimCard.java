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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A SIM Card provisioned under one {@link Contract}'s Fleet (spec.md Solution). {@code
 * monthlyFeeAmount} is set (in the Contract's currency) exactly when {@code flavor} is {@code
 * POSTPAID}, and null for {@code PREPAID} — enforced in {@code SimCardController} and re-checked
 * by a database constraint (V6 migration).
 */
@Entity
@Table(name = "sim_cards")
@Getter
@Setter
@NoArgsConstructor
public class SimCard {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(optional = false)
  @JoinColumn(name = "contract_id", nullable = false)
  private Contract contract;

  @Column(nullable = false)
  private String number;

  @Column private String carrier;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SimCardFlavor flavor;

  @Column(name = "monthly_fee_amount")
  private BigDecimal monthlyFeeAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SimCardStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
