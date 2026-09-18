package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.CarrierOffer;
import com.remotesupport.backend.domain.PostpaidPlan;
import com.remotesupport.backend.domain.TopupOption;
import com.remotesupport.backend.dto.CarrierOfferRequest;
import com.remotesupport.backend.dto.CarrierOfferResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.CarrierOfferRepository;
import com.remotesupport.backend.repository.CarrierRepository;
import com.remotesupport.backend.repository.PostpaidPlanRepository;
import com.remotesupport.backend.repository.TopupOptionRepository;
import com.remotesupport.backend.security.CarrierCatalogAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Carrier's Topup Options and Postpaid Plans (carrier-catalog spec; topup-options-and-postpaid-
 * plans ticket), nested under the Carrier: list, create, edit name and price, archive. The two lists
 * behave identically, so each route delegates to one shared implementation, parameterised by a
 * {@link Kind}. There is deliberately no delete. Access follows the Carrier: another tenant's is a
 * 404, another Country's is a 403 for an Agent ({@link CarrierCatalogAccessGuard}), and a Tester is
 * refused at the matcher level. Nothing under an archived Carrier can be added or changed.
 */
@RestController
@RequestMapping("/api/carriers/{carrierId}")
public class CarrierOfferController {

  /** Active entries first, then cheapest first, then by name. */
  static final Comparator<CarrierOffer> ACTIVE_FIRST_THEN_BY_PRICE =
      Comparator.comparing(CarrierOffer::isArchived)
          .thenComparing(CarrierOffer::getPrice)
          .thenComparing(CarrierOffer::getName, String.CASE_INSENSITIVE_ORDER);

  /** What differs between the two lists: storage, construction, and the words used about them. */
  private record Kind<T extends CarrierOffer>(
      CarrierOfferRepository<T> repository,
      Supplier<T> factory,
      String entity,
      String auditPrefix,
      String nameTakenCode,
      String label) {}

  private final CarrierRepository carrierRepository;
  private final CarrierCatalogAccessGuard accessGuard;
  private final Kind<TopupOption> topupOptions;
  private final Kind<PostpaidPlan> postpaidPlans;

  public CarrierOfferController(
      CarrierRepository carrierRepository,
      TopupOptionRepository topupOptionRepository,
      PostpaidPlanRepository postpaidPlanRepository,
      CarrierCatalogAccessGuard accessGuard) {
    this.carrierRepository = carrierRepository;
    this.accessGuard = accessGuard;
    this.topupOptions =
        new Kind<>(
            topupOptionRepository,
            TopupOption::new,
            "TopupOption",
            "TOPUP_OPTION",
            "TOPUP_OPTION_NAME_TAKEN",
            "Topup Option");
    this.postpaidPlans =
        new Kind<>(
            postpaidPlanRepository,
            PostpaidPlan::new,
            "PostpaidPlan",
            "POSTPAID_PLAN",
            "POSTPAID_PLAN_NAME_TAKEN",
            "Postpaid Plan");
  }

  // --- Topup Options ---

  @GetMapping("/topup-options")
  public List<CarrierOfferResponse> listTopupOptions(
      @PathVariable UUID carrierId,
      @RequestParam(defaultValue = "false") boolean includeArchived,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return list(topupOptions, carrierId, includeArchived, principal);
  }

  @PostMapping("/topup-options")
  @Transactional
  public ResponseEntity<CarrierOfferResponse> createTopupOption(
      @PathVariable UUID carrierId,
      @Valid @RequestBody CarrierOfferRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return create(topupOptions, carrierId, request, principal);
  }

  @PatchMapping("/topup-options/{entryId}")
  @Transactional
  public CarrierOfferResponse editTopupOption(
      @PathVariable UUID carrierId,
      @PathVariable UUID entryId,
      @Valid @RequestBody CarrierOfferRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return edit(topupOptions, carrierId, entryId, request, principal);
  }

  @PostMapping("/topup-options/{entryId}/archive")
  @Transactional
  public CarrierOfferResponse archiveTopupOption(
      @PathVariable UUID carrierId,
      @PathVariable UUID entryId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return archive(topupOptions, carrierId, entryId, principal);
  }

  // --- Postpaid Plans ---

  @GetMapping("/postpaid-plans")
  public List<CarrierOfferResponse> listPostpaidPlans(
      @PathVariable UUID carrierId,
      @RequestParam(defaultValue = "false") boolean includeArchived,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return list(postpaidPlans, carrierId, includeArchived, principal);
  }

  @PostMapping("/postpaid-plans")
  @Transactional
  public ResponseEntity<CarrierOfferResponse> createPostpaidPlan(
      @PathVariable UUID carrierId,
      @Valid @RequestBody CarrierOfferRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return create(postpaidPlans, carrierId, request, principal);
  }

  @PatchMapping("/postpaid-plans/{entryId}")
  @Transactional
  public CarrierOfferResponse editPostpaidPlan(
      @PathVariable UUID carrierId,
      @PathVariable UUID entryId,
      @Valid @RequestBody CarrierOfferRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return edit(postpaidPlans, carrierId, entryId, request, principal);
  }

  @PostMapping("/postpaid-plans/{entryId}/archive")
  @Transactional
  public CarrierOfferResponse archivePostpaidPlan(
      @PathVariable UUID carrierId,
      @PathVariable UUID entryId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return archive(postpaidPlans, carrierId, entryId, principal);
  }

  // --- Shared implementation ---

  /** Active entries only, unless {@code includeArchived} asks for archived ones too. */
  private <T extends CarrierOffer> List<CarrierOfferResponse> list(
      Kind<T> kind, UUID carrierId, boolean includeArchived, AuthenticatedPrincipal principal) {
    Carrier carrier = findReadable(carrierId, principal);
    return kind.repository().findByCarrierId(carrier.getId()).stream()
        .filter(offer -> includeArchived || !offer.isArchived())
        .sorted(ACTIVE_FIRST_THEN_BY_PRICE)
        .map(CarrierOfferResponse::of)
        .toList();
  }

  private <T extends CarrierOffer> ResponseEntity<CarrierOfferResponse> create(
      Kind<T> kind, UUID carrierId, CarrierOfferRequest request, AuthenticatedPrincipal principal) {
    Carrier carrier = findEditable(carrierId, principal);
    String name = request.name().strip();
    requireNameFree(kind, carrier.getId(), name, null);

    T offer = kind.factory().get();
    offer.setId(UUID.randomUUID());
    offer.setCarrier(carrier);
    offer.setName(name);
    offer.setPrice(request.price());
    offer.setCreatedAt(Instant.now());
    save(kind, offer);

    AuditLog.carrierOfferCreated(
        kind.auditPrefix(),
        kind.entity(),
        offer.getId(),
        carrier.getId(),
        name,
        offer.getPrice(),
        principal.userId(),
        principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED).body(CarrierOfferResponse.of(offer));
  }

  /**
   * Changes the name and price of this entry only. SIM Cards and Fees copied the price when they
   * were created, so nothing that already uses the entry moves.
   */
  private <T extends CarrierOffer> CarrierOfferResponse edit(
      Kind<T> kind,
      UUID carrierId,
      UUID entryId,
      CarrierOfferRequest request,
      AuthenticatedPrincipal principal) {
    T offer = findEditableEntry(kind, carrierId, entryId, principal);
    String oldName = offer.getName();
    BigDecimal oldPrice = offer.getPrice();
    String newName = request.name().strip();
    requireNameFree(kind, carrierId, newName, offer.getId());

    offer.setName(newName);
    offer.setPrice(request.price());
    save(kind, offer);

    AuditLog.carrierOfferEdited(
        kind.auditPrefix(),
        kind.entity(),
        offer.getId(),
        carrierId,
        oldName,
        newName,
        oldPrice,
        offer.getPrice(),
        principal.userId(),
        principal.tenantId());

    return CarrierOfferResponse.of(offer);
  }

  private <T extends CarrierOffer> CarrierOfferResponse archive(
      Kind<T> kind, UUID carrierId, UUID entryId, AuthenticatedPrincipal principal) {
    T offer = findEditableEntry(kind, carrierId, entryId, principal);

    offer.setArchivedAt(Instant.now());
    kind.repository().save(offer);

    AuditLog.carrierOfferArchived(
        kind.auditPrefix(),
        kind.entity(),
        offer.getId(),
        carrierId,
        principal.userId(),
        principal.tenantId());

    return CarrierOfferResponse.of(offer);
  }

  /** Under an editable Carrier, reached only through its own Carrier, and not itself archived. */
  private <T extends CarrierOffer> T findEditableEntry(
      Kind<T> kind, UUID carrierId, UUID entryId, AuthenticatedPrincipal principal) {
    Carrier carrier = findEditable(carrierId, principal);
    T offer =
        kind.repository()
            .findByIdAndCarrierId(entryId, carrier.getId())
            .orElseThrow(() -> new NotFoundException("No " + kind.label() + " with id " + entryId));
    if (offer.isArchived()) {
      throw new ConflictException("An archived " + kind.label() + " can't be changed");
    }
    return offer;
  }

  private <T extends CarrierOffer> void save(Kind<T> kind, T offer) {
    try {
      kind.repository().saveAndFlush(offer);
    } catch (DataIntegrityViolationException e) {
      // Two requests racing for one name: V21's unique index refuses the loser.
      throw new OfferNameTakenException(
          kind.nameTakenCode(), "A " + kind.label() + " with this name already exists for this Carrier");
    }
  }

  /** A duplicate name's 409 carries a {@code code}, so the page can show it inline on the field. */
  @ExceptionHandler(OfferNameTakenException.class)
  public ResponseEntity<Map<String, String>> nameTaken(OfferNameTakenException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", e.code, "message", e.getMessage()));
  }

  /** In this tenant, and in a Country the caller may use. */
  private Carrier findReadable(UUID carrierId, AuthenticatedPrincipal principal) {
    Carrier carrier =
        carrierRepository
            .findByIdAndTenantId(carrierId, principal.tenantId())
            .orElseThrow(() -> new NotFoundException("No carrier with id " + carrierId));
    accessGuard.requireCanUse(carrier.getCountry(), principal);
    return carrier;
  }

  /** Readable, and not archived: nothing can be added to or changed under an archived Carrier. */
  private Carrier findEditable(UUID carrierId, AuthenticatedPrincipal principal) {
    Carrier carrier = findReadable(carrierId, principal);
    if (carrier.isArchived()) {
      throw new ConflictException("Nothing can be added to or changed under an archived Carrier");
    }
    return carrier;
  }

  private <T extends CarrierOffer> void requireNameFree(
      Kind<T> kind, UUID carrierId, String name, UUID exceptId) {
    kind.repository()
        .findActiveByName(carrierId, name)
        .filter(existing -> !existing.getId().equals(exceptId))
        .ifPresent(
            existing -> {
              throw new OfferNameTakenException(
                  kind.nameTakenCode(),
                  "A " + kind.label() + " named " + existing.getName() + " already exists for this Carrier");
            });
  }

  /** A name already held by another active entry of the same list, under the same Carrier. */
  static class OfferNameTakenException extends RuntimeException {
    final String code;

    OfferNameTakenException(String code, String message) {
      super(message);
      this.code = code;
    }
  }
}
