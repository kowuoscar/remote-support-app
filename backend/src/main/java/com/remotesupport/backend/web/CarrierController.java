package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.dto.CarrierCatalogResponse;
import com.remotesupport.backend.dto.CarrierCreateRequest;
import com.remotesupport.backend.dto.CarrierRenameRequest;
import com.remotesupport.backend.dto.CarrierResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.CarrierRepository;
import com.remotesupport.backend.repository.TenantRepository;
import com.remotesupport.backend.security.CarrierCatalogAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
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
 * A Country's Carriers (carrier-catalog spec; agent-maintains-carriers ticket): list, create,
 * rename and archive. There is deliberately no delete — a Carrier is archived, and archiving is
 * one-way. Tenant scoping comes first (another tenant's Carrier is a 404), then the per-Country
 * check in {@link CarrierCatalogAccessGuard} (another Country's is a 403 for an Agent).
 */
@RestController
@RequestMapping("/api/carriers")
public class CarrierController {

  static final String NAME_TAKEN = "CARRIER_NAME_TAKEN";

  private static final Comparator<Carrier> ACTIVE_FIRST_THEN_BY_NAME =
      Comparator.comparing(Carrier::isArchived)
          .thenComparing(Carrier::getName, String.CASE_INSENSITIVE_ORDER);

  private final CarrierRepository carrierRepository;
  private final TenantRepository tenantRepository;
  private final CarrierCatalogAccessGuard accessGuard;

  public CarrierController(
      CarrierRepository carrierRepository,
      TenantRepository tenantRepository,
      CarrierCatalogAccessGuard accessGuard) {
    this.carrierRepository = carrierRepository;
    this.tenantRepository = tenantRepository;
    this.accessGuard = accessGuard;
  }

  /** Active Carriers only, unless {@code includeArchived} asks for archived ones too. */
  @GetMapping
  public CarrierCatalogResponse list(
      @RequestParam(required = false) Country country,
      @RequestParam(defaultValue = "false") boolean includeArchived,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Country catalogCountry = resolveCountry(country, principal);
    return new CarrierCatalogResponse(
        catalogCountry.name(),
        catalogCountry.currency().name(),
        carrierRepository.findByTenantIdAndCountry(principal.tenantId(), catalogCountry).stream()
            .filter(carrier -> includeArchived || !carrier.isArchived())
            .sorted(ACTIVE_FIRST_THEN_BY_NAME)
            .map(CarrierResponse::of)
            .toList());
  }

  @PostMapping
  @Transactional
  public ResponseEntity<CarrierResponse> create(
      @Valid @RequestBody CarrierCreateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Country country = resolveCountry(request.country(), principal);
    String name = request.name().strip();
    requireNameFree(principal.tenantId(), country, name, null);

    Carrier carrier = new Carrier();
    carrier.setId(UUID.randomUUID());
    carrier.setTenant(tenantRepository.getReferenceById(principal.tenantId()));
    carrier.setCountry(country);
    carrier.setName(name);
    carrier.setCreatedAt(Instant.now());
    carrierRepository.saveAndFlush(carrier);

    AuditLog.carrierCreated(
        carrier.getId(), country.name(), name, principal.userId(), principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED).body(CarrierResponse.of(carrier));
  }

  @PatchMapping("/{carrierId}")
  @Transactional
  public CarrierResponse rename(
      @PathVariable UUID carrierId,
      @Valid @RequestBody CarrierRenameRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Carrier carrier = findEditable(carrierId, principal);
    String oldName = carrier.getName();
    String newName = request.name().strip();
    requireNameFree(principal.tenantId(), carrier.getCountry(), newName, carrier.getId());

    carrier.setName(newName);
    carrierRepository.saveAndFlush(carrier);

    AuditLog.carrierRenamed(
        carrier.getId(),
        carrier.getCountry().name(),
        oldName,
        newName,
        principal.userId(),
        principal.tenantId());

    return CarrierResponse.of(carrier);
  }

  @PostMapping("/{carrierId}/archive")
  @Transactional
  public CarrierResponse archive(
      @PathVariable UUID carrierId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Carrier carrier = findEditable(carrierId, principal);

    carrier.setArchivedAt(Instant.now());
    carrierRepository.save(carrier);

    AuditLog.carrierArchived(
        carrier.getId(), carrier.getCountry().name(), principal.userId(), principal.tenantId());

    return CarrierResponse.of(carrier);
  }

  /** A duplicate name's 409 carries a {@code code}, so the page can show it inline on the field. */
  @ExceptionHandler(CarrierNameTakenException.class)
  public ResponseEntity<Map<String, String>> nameTaken(CarrierNameTakenException e) {
    return nameTakenResponse(e.getMessage());
  }

  /** Two requests racing for one name: V19's unique index refuses the loser. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> nameRaceLost(DataIntegrityViolationException e) {
    return nameTakenResponse("A Carrier with this name already exists in this Country");
  }

  private ResponseEntity<Map<String, String>> nameTakenResponse(String message) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", NAME_TAKEN, "message", message));
  }

  private Country resolveCountry(Country requested, AuthenticatedPrincipal principal) {
    Country country = accessGuard.requireCanUse(requested, principal);
    if (country == null) {
      throw new InvalidRequestException("country is required");
    }
    return country;
  }

  /** In this tenant, in a Country the caller may edit, and not archived. */
  private Carrier findEditable(UUID carrierId, AuthenticatedPrincipal principal) {
    Carrier carrier =
        carrierRepository
            .findByIdAndTenantId(carrierId, principal.tenantId())
            .orElseThrow(() -> new NotFoundException("No carrier with id " + carrierId));
    accessGuard.requireCanUse(carrier.getCountry(), principal);
    if (carrier.isArchived()) {
      throw new ConflictException("An archived Carrier can't be changed");
    }
    return carrier;
  }

  private void requireNameFree(UUID tenantId, Country country, String name, UUID exceptCarrierId) {
    carrierRepository
        .findActiveByName(tenantId, country, name)
        .filter(existing -> !existing.getId().equals(exceptCarrierId))
        .ifPresent(
            existing -> {
              throw new CarrierNameTakenException(
                  "A Carrier named " + existing.getName() + " already exists in this Country");
            });
  }

  /** A name already held by another active Carrier of the same Country. */
  static class CarrierNameTakenException extends RuntimeException {
    CarrierNameTakenException(String message) {
      super(message);
    }
  }
}
