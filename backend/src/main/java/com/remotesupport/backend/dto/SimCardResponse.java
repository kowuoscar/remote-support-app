package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.remotesupport.backend.domain.SimCard;
import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimCardResponse(
    UUID id,
    UUID contractId,
    String number,
    String carrier,
    String flavor,
    BigDecimal monthlyFeeAmount,
    String status) {

  public static SimCardResponse of(SimCard simCard) {
    return new SimCardResponse(
        simCard.getId(),
        simCard.getContract().getId(),
        simCard.getNumber(),
        simCard.getCarrier(),
        simCard.getFlavor().name(),
        simCard.getMonthlyFeeAmount(),
        simCard.getStatus().name());
  }
}
