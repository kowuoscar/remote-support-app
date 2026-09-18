package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Carrier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A Carrier as the catalog lists it: {@link CarrierResponse}'s fields plus the Carrier's Topup
 * Options and Postpaid Plans, so the Carriers page (and a picker) needs one request per Country.
 * The lists follow the catalog's own {@code includeArchived}: active entries only by default,
 * archived ones too on request — active first, then cheapest first, then by name.
 */
public record CatalogCarrierResponse(
    UUID id,
    String country,
    String name,
    Instant archivedAt,
    List<CarrierOfferResponse> topupOptions,
    List<CarrierOfferResponse> postpaidPlans) {

  public static CatalogCarrierResponse of(
      Carrier carrier, List<CarrierOfferResponse> topupOptions, List<CarrierOfferResponse> postpaidPlans) {
    return new CatalogCarrierResponse(
        carrier.getId(),
        carrier.getCountry().name(),
        carrier.getName(),
        carrier.getArchivedAt(),
        topupOptions,
        postpaidPlans);
  }
}
