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
 * POSTPAID}, and null for {@code PREPAID} — enforced in {@code SimCardFactory} and re-checked by a
 * database constraint (V6 migration). Since the Carrier catalog it is copied from {@code
 * postpaidPlan}'s price at creation and never re-read, so a later Plan price change leaves it, and
 * every Client Invoice it feeds, untouched.
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

  /**
   * The Carrier this SIM Card is on, from its Contract's Agent's Country. Every SIM Card created
   * since the Carrier catalog names one; one created before it may have none (V23 migration).
   */
  @ManyToOne
  @JoinColumn(name = "carrier_id")
  private Carrier carrier;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SimCardFlavor flavor;

  /**
   * The Postpaid Plan this SIM Card's monthly fee was copied from, for a Postpaid SIM created
   * since the Carrier catalog. Null for a Prepaid SIM, and for a Postpaid SIM from before the
   * catalog, which keeps its typed-in fee (V26 migration).
   */
  @ManyToOne
  @JoinColumn(name = "postpaid_plan_id")
  private PostpaidPlan postpaidPlan;

  @Column(name = "monthly_fee_amount")
  private BigDecimal monthlyFeeAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SimCardStatus status;

  /**
   * The Smartphone this SIM Card currently sits in, or {@code null} when it isn't installed
   * anywhere (spec.md Solution — Fleet model: "Installed in"; sim-installed-in-smartphone
   * ticket). Set, cleared and moved exclusively through {@link
   * com.remotesupport.backend.web.SimInstallationService} — the one module that owns installing,
   * uninstalling and the two-SIM-per-Smartphone check, so no write path can skip either.
   */
  @ManyToOne
  @JoinColumn(name = "installed_in_smartphone_id")
  private Smartphone installedInSmartphone;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** This SIM Card's Postpaid Plan id, or {@code null} when it has none — the data lives here. */
  public UUID postpaidPlanId() {
    return postpaidPlan == null ? null : postpaidPlan.getId();
  }

  /** This SIM Card's Installed-in Smartphone id, or {@code null} when it isn't installed. */
  public UUID installedInSmartphoneId() {
    return installedInSmartphone == null ? null : installedInSmartphone.getId();
  }
}
