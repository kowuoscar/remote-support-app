package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.PostpaidPlan;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.Smartphone;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One Smartphone or SIM Card in an Agent's Stock (returns-and-agent-stock spec, Solution's Agent
 * Stock; agent-stock ticket AC: "model, serial, number, Carrier, flavor, Plan and the Contract
 * each came from"). Exactly one of the Smartphone-only fields ({@code model}, {@code serial}) or
 * the SIM-Card-only fields ({@code number}, {@code carrier*}, {@code flavor}, {@code
 * postpaidPlan*}, {@code monthlyFeeAmount}) is present, matching {@link SmartphoneResponse}'s and
 * {@link SimCardResponse}'s own shapes — {@code kind} says which. {@code fromContractId}/{@code
 * fromClientName} are absent for a unit whose origin can't be found (defensive; every real Stock
 * unit has one, per {@code ReturnCompletionEffect}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockUnitResponse(
    UUID id,
    String kind,
    UUID agentId,
    String agentName,
    String agentCurrency,
    String model,
    String serial,
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
    UUID fromContractId,
    String fromClientName) {

  public static StockUnitResponse of(Smartphone smartphone, Contract fromContract) {
    Agent agent = smartphone.getHoldingAgent();
    return new StockUnitResponse(
        smartphone.getId(),
        "SMARTPHONE",
        agent == null ? null : agent.getId(),
        agent == null ? null : agent.getName(),
        agent == null ? null : agent.getCurrency().name(),
        smartphone.getModel(),
        smartphone.getSerial(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        smartphone.getStatus().name(),
        fromContract == null ? null : fromContract.getId(),
        fromContract == null ? null : fromContract.getClient().getName());
  }

  public static StockUnitResponse of(SimCard simCard, Contract fromContract) {
    Agent agent = simCard.getHoldingAgent();
    Carrier carrier = simCard.getCarrier();
    PostpaidPlan plan = simCard.getPostpaidPlan();
    return new StockUnitResponse(
        simCard.getId(),
        "SIM_CARD",
        agent == null ? null : agent.getId(),
        agent == null ? null : agent.getName(),
        agent == null ? null : agent.getCurrency().name(),
        null,
        null,
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
        fromContract == null ? null : fromContract.getId(),
        fromContract == null ? null : fromContract.getClient().getName());
  }
}
