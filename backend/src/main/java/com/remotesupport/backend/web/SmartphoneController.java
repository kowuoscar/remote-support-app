package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.dto.SmartphoneCreateRequest;
import com.remotesupport.backend.dto.SmartphoneResponse;
import com.remotesupport.backend.dto.SmartphoneStatusUpdateRequest;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.security.FleetAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

  public SmartphoneController(
      ContractRepository contractRepository,
      SmartphoneRepository smartphoneRepository,
      FleetAccessGuard fleetAccessGuard) {
    this.contractRepository = contractRepository;
    this.smartphoneRepository = smartphoneRepository;
    this.fleetAccessGuard = fleetAccessGuard;
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
    smartphone.setSerial(request.serial());
    smartphone.setAssignedTo(request.assignedTo());
    smartphone.setStatus(SmartphoneStatus.ACTIVE);
    smartphone.setCreatedAt(Instant.now());
    smartphoneRepository.save(smartphone);

    AuditLog.created("Smartphone", smartphone.getId(), principal.userId(), principal.tenantId());

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

    return SmartphoneResponse.of(smartphone);
  }

  private Contract findContract(UUID contractId, AuthenticatedPrincipal principal) {
    return contractRepository
        .findByIdAndTenantId(contractId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No contract with id " + contractId));
  }
}
