package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.CarrierOffer;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.dto.CarrierCatalogResponse;
import com.remotesupport.backend.dto.CarrierOfferResponse;
import com.remotesupport.backend.dto.CatalogCarrierResponse;
import com.remotesupport.backend.repository.CarrierRepository;
import com.remotesupport.backend.repository.PostpaidPlanRepository;
import com.remotesupport.backend.repository.TopupOptionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds one Country's {@link CarrierCatalogResponse} — its Carriers, each with its Topup Options
 * and Postpaid Plans (carrier-catalog spec). Extracted out of {@link CarrierController} (which
 * still owns writes and the Country-scoped read) so {@link ContractCarrierController}'s
 * Contract-scoped read (reboot-and-topup-details ticket AC: "Anyone who can view a Contract can
 * read the active Carrier catalog of that Contract's Country") builds the identical shape without
 * duplicating the query/grouping logic.
 */
@Component
public class CarrierCatalogService {

  private static final Comparator<Carrier> ACTIVE_FIRST_THEN_BY_NAME =
      Comparator.comparing(Carrier::isArchived)
          .thenComparing(Carrier::getName, String.CASE_INSENSITIVE_ORDER);

  private final CarrierRepository carrierRepository;
  private final TopupOptionRepository topupOptionRepository;
  private final PostpaidPlanRepository postpaidPlanRepository;

  public CarrierCatalogService(
      CarrierRepository carrierRepository,
      TopupOptionRepository topupOptionRepository,
      PostpaidPlanRepository postpaidPlanRepository) {
    this.carrierRepository = carrierRepository;
    this.topupOptionRepository = topupOptionRepository;
    this.postpaidPlanRepository = postpaidPlanRepository;
  }

  /** Active Carriers of {@code country} only, unless {@code includeArchived} asks for archived ones too. */
  public CarrierCatalogResponse buildCatalog(UUID tenantId, Country country, boolean includeArchived) {
    List<Carrier> carriers =
        carrierRepository.findByTenantIdAndCountry(tenantId, country).stream()
            .filter(carrier -> includeArchived || !carrier.isArchived())
            .sorted(ACTIVE_FIRST_THEN_BY_NAME)
            .toList();
    List<UUID> carrierIds = carriers.stream().map(Carrier::getId).toList();
    Map<UUID, List<CarrierOfferResponse>> topupOptions =
        byCarrier(topupOptionRepository.findByCarrierIdIn(carrierIds), includeArchived);
    Map<UUID, List<CarrierOfferResponse>> postpaidPlans =
        byCarrier(postpaidPlanRepository.findByCarrierIdIn(carrierIds), includeArchived);
    return new CarrierCatalogResponse(
        country.name(),
        country.currency().name(),
        carriers.stream()
            .map(
                carrier ->
                    CatalogCarrierResponse.of(
                        carrier,
                        topupOptions.getOrDefault(carrier.getId(), List.of()),
                        postpaidPlans.getOrDefault(carrier.getId(), List.of())))
            .toList());
  }

  private static Map<UUID, List<CarrierOfferResponse>> byCarrier(
      List<? extends CarrierOffer> offers, boolean includeArchived) {
    return offers.stream()
        .filter(offer -> includeArchived || !offer.isArchived())
        .sorted(CarrierOfferController.ACTIVE_FIRST_THEN_BY_PRICE)
        .collect(
            Collectors.groupingBy(
                offer -> offer.getCarrier().getId(),
                Collectors.mapping(CarrierOfferResponse::of, Collectors.toList())));
  }
}
