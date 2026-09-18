package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.SimCard;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The Carrier travels as its id, name and whether it is archived, so a reader never needs the
 * catalog (carrier-catalog spec, SIM Card changes). All three are absent for a SIM Card from
 * before the catalog that never had a carrier.
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
    BigDecimal monthlyFeeAmount,
    String status) {

  public static SimCardResponse of(SimCard simCard) {
    Carrier carrier = simCard.getCarrier();
    return new SimCardResponse(
        simCard.getId(),
        simCard.getContract().getId(),
        simCard.getNumber(),
        carrier == null ? null : carrier.getId(),
        carrier == null ? null : carrier.getName(),
        carrier == null ? null : carrier.isArchived(),
        simCard.getFlavor().name(),
        simCard.getMonthlyFeeAmount(),
        simCard.getStatus().name());
  }
}
