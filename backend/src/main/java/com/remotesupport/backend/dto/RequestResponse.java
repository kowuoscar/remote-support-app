package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.PostpaidPlan;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.TopupOption;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The target Smartphone/SIM Card/Topup Option fields (reboot-and-topup-details ticket) travel
 * denormalized — id plus the one human-readable field a Requests list row needs — the same
 * "reader never needs the catalog" shape {@link SimCardResponse} already established for a SIM
 * Card's Carrier/Plan, so the Tester's and Agent's Requests lists can summarise a row without a
 * second round-trip. Absent (never present in the JSON, via {@link JsonInclude}) for every type
 * but the one that set them, and for a Request that existed before this ticket.
 *
 * <p>{@code requestedModel}/{@code requestedFlavor}/{@code requestedCarrier*}/{@code
 * requestedPostpaidPlan*} (provision-request-details ticket) are Provision Smartphone's and
 * Provision SIM's own requested details, in the same denormalized shape — the Carrier/Plan travel
 * with their name and archived flag, exactly like {@link SimCardResponse}'s own Carrier/Plan
 * fields, so a Requests list or the Agent's completion step never needs a catalog round-trip. A
 * Provision SIM's optional target Smartphone reuses {@code targetSmartphoneId}/{@code
 * targetSmartphoneModel} above. {@code completionNote} rides back only on the one response a
 * completion PATCH itself returns (see {@link Request#getCompletionNote}).
 *
 * <p>{@code secondSimCardId}/{@code secondSimCardNumber}/{@code secondTargetSmartphoneId}/{@code
 * secondTargetSmartphoneModel} (sim-swap-moves ticket) are a SIM Swap exchange's second move —
 * {@code targetSimCardId}/{@code targetSmartphoneId} above double as its first. Absent for a plain
 * single move, every other type, and a SIM Swap Request that existed before this ticket.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestResponse(
    UUID id,
    UUID contractId,
    String type,
    String status,
    UUID raisedByTesterId,
    String raisedByUsername,
    boolean agentAuthored,
    String loggedByUsername,
    String cancellationReason,
    String description,
    Instant createdAt,
    UUID targetSmartphoneId,
    String targetSmartphoneModel,
    UUID targetSimCardId,
    String targetSimCardNumber,
    UUID topupOptionId,
    String topupOptionName,
    BigDecimal topupOptionPrice,
    String requestedModel,
    String requestedFlavor,
    UUID requestedCarrierId,
    String requestedCarrierName,
    Boolean requestedCarrierArchived,
    UUID requestedPostpaidPlanId,
    String requestedPostpaidPlanName,
    Boolean requestedPostpaidPlanArchived,
    String completionNote,
    UUID secondSimCardId,
    String secondSimCardNumber,
    UUID secondTargetSmartphoneId,
    String secondTargetSmartphoneModel) {

  public static RequestResponse of(Request request) {
    Smartphone targetSmartphone = request.getTargetSmartphone();
    SimCard targetSimCard = request.getTargetSimCard();
    TopupOption topupOption = request.getTopupOption();
    Carrier requestedCarrier = request.getRequestedCarrier();
    PostpaidPlan requestedPlan = request.getRequestedPostpaidPlan();
    SimCard secondSimCard = request.getSecondSimCard();
    Smartphone secondTargetSmartphone = request.getSecondTargetSmartphone();
    return new RequestResponse(
        request.getId(),
        request.getContract().getId(),
        request.getType().name(),
        request.getStatus().name(),
        request.getTester().getId(),
        request.getTester().getUser().getUsername(),
        request.isAgentAuthored(),
        request.getRaisedByUser().getUsername(),
        request.getCancellationReason(),
        request.getDescription(),
        request.getCreatedAt(),
        targetSmartphone == null ? null : targetSmartphone.getId(),
        targetSmartphone == null ? null : targetSmartphone.getModel(),
        targetSimCard == null ? null : targetSimCard.getId(),
        targetSimCard == null ? null : targetSimCard.getNumber(),
        topupOption == null ? null : topupOption.getId(),
        topupOption == null ? null : topupOption.getName(),
        topupOption == null ? null : topupOption.getPrice(),
        request.getRequestedModel(),
        request.getRequestedFlavor() == null ? null : request.getRequestedFlavor().name(),
        requestedCarrier == null ? null : requestedCarrier.getId(),
        requestedCarrier == null ? null : requestedCarrier.getName(),
        requestedCarrier == null ? null : requestedCarrier.isArchived(),
        requestedPlan == null ? null : requestedPlan.getId(),
        requestedPlan == null ? null : requestedPlan.getName(),
        requestedPlan == null ? null : requestedPlan.isArchived(),
        request.getCompletionNote(),
        secondSimCard == null ? null : secondSimCard.getId(),
        secondSimCard == null ? null : secondSimCard.getNumber(),
        secondTargetSmartphone == null ? null : secondTargetSmartphone.getId(),
        secondTargetSmartphone == null ? null : secondTargetSmartphone.getModel());
  }
}
