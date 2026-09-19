package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.PostpaidPlan;
import com.remotesupport.backend.domain.SimCard;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The Carrier and, for a Postpaid SIM, the Postpaid Plan travel as their id, name and whether they
 * are archived, so a reader never needs the catalog (carrier-catalog spec, SIM Card changes). The
 * Carrier's three are absent for a SIM Card from before the catalog that never had a carrier, and
 * the Plan's for a Prepaid SIM or a Postpaid SIM from before the catalog.
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
    String status) {

  public static SimCardResponse of(SimCard simCard) {
    Carrier carrier = simCard.getCarrier();
    PostpaidPlan plan = simCard.getPostpaidPlan();
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
        simCard.getStatus().name());
  }
}
