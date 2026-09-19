package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.CarrierOffer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A Topup Option or a Postpaid Plan. {@code price} is in the Carrier's Country's currency (the
 * catalog response names it); {@code archivedAt} is null while the entry is active.
 */
public record CarrierOfferResponse(
    UUID id, UUID carrierId, String name, BigDecimal price, Instant archivedAt) {

  public static CarrierOfferResponse of(CarrierOffer offer) {
    return new CarrierOfferResponse(
        offer.getId(),
        offer.getCarrier().getId(),
        offer.getName(),
        offer.getPrice(),
        offer.getArchivedAt());
  }
}
