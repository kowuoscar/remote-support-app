package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.remotesupport.backend.domain.ReturnedUnit;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.Smartphone;
import java.util.UUID;

/**
 * One unit named on a {@code RETURN} Request, denormalized the same "reader never needs a second
 * round-trip" way as {@link RequestResponse}'s own target/requested fields (return-client-owned-smartphones
 * ticket AC: "shown on the Request in every Requests list"). Exactly one of {@code
 * smartphoneId}/{@code simCardId} is present, matching {@link ReturnedUnit}'s own shape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReturnedUnitResponse(
    UUID id,
    UUID smartphoneId,
    String smartphoneModel,
    UUID simCardId,
    String simCardNumber,
    String disposition) {

  public static ReturnedUnitResponse of(ReturnedUnit unit) {
    Smartphone smartphone = unit.getSmartphone();
    SimCard simCard = unit.getSimCard();
    return new ReturnedUnitResponse(
        unit.getId(),
        smartphone == null ? null : smartphone.getId(),
        smartphone == null ? null : smartphone.getModel(),
        simCard == null ? null : simCard.getId(),
        simCard == null ? null : simCard.getNumber(),
        unit.getDisposition() == null ? null : unit.getDisposition().name());
  }
}
