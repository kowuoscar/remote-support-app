package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneOwner;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.dto.SmartphoneCreateRequest;
import com.remotesupport.backend.dto.SmartphoneResponse;
import com.remotesupport.backend.dto.SmartphoneModelUpdateRequest;
import com.remotesupport.backend.dto.SmartphoneOwnerUpdateRequest;
import com.remotesupport.backend.dto.SmartphoneSerialUpdateRequest;
import com.remotesupport.backend.dto.SmartphoneStatusUpdateRequest;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.security.FleetAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smartphones on one Contract's Fleet (fleet-management ticket). Create is Manager-only,
 * enforced at the request-matcher level in {@link com.remotesupport.backend.security.SecurityConfig};
 * viewing and status changes are scoped per-Contract via {@link FleetAccessGuard} since that
 * ownership check can't be expressed as a URL pattern.
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/smartphones")
public class SmartphoneController {

  private final ContractRepository contractRepository;
  private final SmartphoneRepository smartphoneRepository;
  private final FleetAccessGuard fleetAccessGuard;
  private final SimInstallationService simInstallationService;
  private final SmartphoneCorrectionService correctionService;

  public SmartphoneController(
      ContractRepository contractRepository,
      SmartphoneRepository smartphoneRepository,
      FleetAccessGuard fleetAccessGuard,
      SimInstallationService simInstallationService,
      SmartphoneCorrectionService correctionService) {
    this.contractRepository = contractRepository;
    this.smartphoneRepository = smartphoneRepository;
    this.fleetAccessGuard = fleetAccessGuard;
    this.simInstallationService = simInstallationService;
    this.correctionService = correctionService;
  }

  @PostMapping
  public ResponseEntity<SmartphoneResponse> create(
      @PathVariable UUID contractId,
      @Valid @RequestBody SmartphoneCreateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);

    Smartphone smartphone = new Smartphone();
    smartphone.setId(UUID.randomUUID());
    smartphone.setTenant(contract.getTenant());
    smartphone.setContract(contract);
    smartphone.setModel(request.model());
    smartphone.setSerial(blankToNull(request.serial()));
    smartphone.setOwner(request.owner() != null ? request.owner() : SmartphoneOwner.COMPANY);
    smartphone.setStatus(SmartphoneStatus.ACTIVE);
    smartphone.setCreatedAt(Instant.now());
    smartphoneRepository.save(smartphone);

    AuditLog.smartphoneCreated(
        smartphone.getId(),
        contract.getId(),
        smartphone.getOwner().name(),
        principal.userId(),
        principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED).body(SmartphoneResponse.of(smartphone));
  }

  @GetMapping
  public List<SmartphoneResponse> list(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    fleetAccessGuard.requireCanView(contract, principal);

    return smartphoneRepository.findByContractIdOrderByCreatedAtAsc(contractId).stream()
        .map(SmartphoneResponse::of)
        .toList();
  }

  @PatchMapping("/{smartphoneId}/status")
  public SmartphoneResponse updateStatus(
      @PathVariable UUID contractId,
      @PathVariable UUID smartphoneId,
      @Valid @RequestBody SmartphoneStatusUpdateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    fleetAccessGuard.requireCanChangeStatus(contract, principal);

    Smartphone smartphone =
        smartphoneRepository
            .findByIdAndContractId(smartphoneId, contractId)
            .orElseThrow(() -> new NotFoundException("No smartphone with id " + smartphoneId));

    SmartphoneStatus oldStatus = smartphone.getStatus();
    SmartphoneStatus newStatus = request.status();
    if (!oldStatus.canTransitionTo(newStatus)) {
      throw new ConflictException(
          "Cannot transition a Smartphone from " + oldStatus + " to " + newStatus);
    }

    smartphone.setStatus(newStatus);
    smartphoneRepository.save(smartphone);

    AuditLog.statusChanged(
        "Smartphone",
        smartphone.getId(),
        oldStatus.name(),
        newStatus.name(),
        principal.userId(),
        principal.tenantId());

    // Retiring a Smartphone clears the Installed-in link on its SIM Cards (spec.md Solution —
    // Fleet model; sim-installed-in-smartphone ticket AC).
    if (newStatus == SmartphoneStatus.RETIRED) {
      simInstallationService.clearLinksForRetiredSmartphone(smartphone, null, principal);
    }

    return SmartphoneResponse.of(smartphone);
  }

  /**
   * Sets or changes a Smartphone's serial from the Fleet page (smartphone-owner-and-optional-serial
   * ticket AC: "the Agent (own Contract) or the Manager can set or change the serial later ...
   * a Tester cannot"). Same allowed callers as a status change, so this reuses {@link
   * FleetAccessGuard#requireCanChangeStatus} rather than adding a same-shaped guard method.
   */
  @PatchMapping("/{smartphoneId}/serial")
  public SmartphoneResponse updateSerial(
      @PathVariable UUID contractId,
      @PathVariable UUID smartphoneId,
      @Valid @RequestBody SmartphoneSerialUpdateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    fleetAccessGuard.requireCanChangeStatus(contract, principal);

    Smartphone smartphone =
        smartphoneRepository
            .findByIdAndContractId(smartphoneId, contractId)
            .orElseThrow(() -> new NotFoundException("No smartphone with id " + smartphoneId));

    String oldSerial = smartphone.getSerial();
    String newSerial = blankToNull(request.serial());
    smartphone.setSerial(newSerial);
    smartphoneRepository.save(smartphone);

    AuditLog.smartphoneSerialChanged(
        smartphone.getId(), oldSerial, newSerial, principal.userId(), principal.tenantId());

    return SmartphoneResponse.of(smartphone);
  }

  /**
   * Corrects a Smartphone's model (correct-a-fleet-unit spec): the Manager, or a Tester of the
   * Contract's own Client, through {@link FleetAccessGuard#requireCanCorrect}.
   */
  @PatchMapping("/{smartphoneId}/model")
  public SmartphoneResponse correctModel(
      @PathVariable UUID contractId,
      @PathVariable UUID smartphoneId,
      @Valid @RequestBody SmartphoneModelUpdateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    fleetAccessGuard.requireCanCorrect(contract, principal);

    return SmartphoneResponse.of(
        correctionService.correctModel(contract, smartphoneId, request.model(), principal));
  }

  /**
   * Corrects a Smartphone's owner (correct-a-fleet-unit spec); refused with a coded 409 while an
   * open Return names the phone.
   */
  @PatchMapping("/{smartphoneId}/owner")
  public SmartphoneResponse correctOwner(
      @PathVariable UUID contractId,
      @PathVariable UUID smartphoneId,
      @Valid @RequestBody SmartphoneOwnerUpdateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    fleetAccessGuard.requireCanCorrect(contract, principal);

    return SmartphoneResponse.of(
        correctionService.correctOwner(contract, smartphoneId, request.owner(), principal));
  }

  /** A correction that clashes with the unit's state: 409 with a {@code code}. */
  @ExceptionHandler(CorrectionConflictException.class)
  public ResponseEntity<Map<String, String>> correctionConflict(CorrectionConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", e.getCode(), "message", e.getMessage()));
  }

  /** A refused correction's 400 carries a {@code code}, so the page can show it at the field. */
  @ExceptionHandler(InvalidCorrectionException.class)
  public ResponseEntity<Map<String, String>> invalidCorrection(InvalidCorrectionException e) {
    return CorrectionErrors.badRequest(e);
  }

  /** A correction request whose DTO fails Bean Validation: the same coded 400. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> correctionRequestInvalid(MethodArgumentNotValidException e) {
    return CorrectionErrors.badRequest(e);
  }

  private static String blankToNull(String value) {
    return StringUtils.hasText(value) ? value : null;
  }

  private Contract findContract(UUID contractId, AuthenticatedPrincipal principal) {
    return contractRepository
        .findByIdAndTenantId(contractId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No contract with id " + contractId));
  }
}
