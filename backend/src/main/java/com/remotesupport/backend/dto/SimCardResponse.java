package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.PostpaidPlan;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.Smartphone;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The Carrier and, for a Postpaid SIM, the Postpaid Plan travel as their id, name and whether they
 * are archived, so a reader never needs the catalog (carrier-catalog spec, SIM Card changes). The
 * Carrier's three are absent for a SIM Card from before the catalog that never had a carrier, and
 * the Plan's for a Prepaid SIM or a Postpaid SIM from before the catalog. {@code
 * installedInSmartphoneId}/{@code installedInSmartphoneModel} are absent when the SIM Card isn't
 * Installed in any Smartphone (spec.md Solution — Fleet model: "Installed in";
 * sim-installed-in-smartphone ticket).
 *
 * <p>{@code cancellationEffectiveDate} (returns-and-agent-stock spec, Solution's Completion table;
 * manager-decides-return-disposition ticket AC: "keeps its cancellation date, shown in the Fleet
 * tables") is absent for every SIM Card except one retired through a cancelled Return.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimCardResponse(
    UUID id,
    UUID contractId,
    String number,
    UUID carrierId,
    String carrierName,
    Boolean carrierArchived,
    String flavor,
    UUID postpaidPlanId,
    String postpaidPlanName,
    Boolean postpaidPlanArchived,
    BigDecimal monthlyFeeAmount,
    String status,
    UUID installedInSmartphoneId,
    String installedInSmartphoneModel,
    LocalDate cancellationEffectiveDate) {

  public static SimCardResponse of(SimCard simCard) {
    Carrier carrier = simCard.getCarrier();
    PostpaidPlan plan = simCard.getPostpaidPlan();
    Smartphone installedIn = simCard.getInstalledInSmartphone();
    return new SimCardResponse(
        simCard.getId(),
        simCard.getContract().getId(),
        simCard.getNumber(),
        carrier == null ? null : carrier.getId(),
        carrier == null ? null : carrier.getName(),
        carrier == null ? null : carrier.isArchived(),
        simCard.getFlavor().name(),
        plan == null ? null : plan.getId(),
        plan == null ? null : plan.getName(),
        plan == null ? null : plan.isArchived(),
        simCard.getMonthlyFeeAmount(),
        simCard.getStatus().name(),
        installedIn == null ? null : installedIn.getId(),
        installedIn == null ? null : installedIn.getModel(),
        simCard.getCancellationEffectiveDate());
  }
}
