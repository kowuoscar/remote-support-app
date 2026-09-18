package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    BigDecimal topupOptionPrice) {

  public static RequestResponse of(Request request) {
    Smartphone targetSmartphone = request.getTargetSmartphone();
    SimCard targetSimCard = request.getTargetSimCard();
    TopupOption topupOption = request.getTopupOption();
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
        topupOption == null ? null : topupOption.getPrice());
  }
}
