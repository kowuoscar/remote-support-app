package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.dto.SimCardCreateRequest;
import com.remotesupport.backend.dto.SimCardResponse;
import com.remotesupport.backend.dto.SimCardStatusUpdateRequest;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.security.FleetAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
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
 * SIM Cards on one Contract's Fleet (fleet-management ticket). Create is Manager-only, enforced
 * at the request-matcher level in {@link com.remotesupport.backend.security.SecurityConfig};
 * viewing and status changes are scoped per-Contract via {@link FleetAccessGuard}. Mirrors
 * {@link SmartphoneController}'s shape.
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/sim-cards")
public class SimCardController {

  private final ContractRepository contractRepository;
  private final SimCardRepository simCardRepository;
  private final FleetAccessGuard fleetAccessGuard;
  private final SimCardFactory simCardFactory;

  public SimCardController(
      ContractRepository contractRepository,
      SimCardRepository simCardRepository,
      FleetAccessGuard fleetAccessGuard,
      SimCardFactory simCardFactory) {
    this.contractRepository = contractRepository;
    this.simCardRepository = simCardRepository;
    this.fleetAccessGuard = fleetAccessGuard;
    this.simCardFactory = simCardFactory;
  }

  @PostMapping
  public ResponseEntity<SimCardResponse> create(
      @PathVariable UUID contractId,
      @Valid @RequestBody SimCardCreateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);

    SimCard simCard = simCardFactory.create(contract, request);

    AuditLog.simCardCreated(
        simCard.getId(),
        contract.getId(),
        simCard.getCarrier().getId(),
        simCard.postpaidPlanId(),
        simCard.getMonthlyFeeAmount(),
        principal.userId(),
        principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED).body(SimCardResponse.of(simCard));
  }

  @GetMapping
  public List<SimCardResponse> list(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    fleetAccessGuard.requireCanView(contract, principal);

    return simCardRepository.findByContractIdOrderByCreatedAtAsc(contractId).stream()
        .map(SimCardResponse::of)
        .toList();
  }

  @PatchMapping("/{simCardId}/status")
  public SimCardResponse updateStatus(
      @PathVariable UUID contractId,
      @PathVariable UUID simCardId,
      @Valid @RequestBody SimCardStatusUpdateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    fleetAccessGuard.requireCanChangeStatus(contract, principal);

    SimCard simCard =
        simCardRepository
            .findByIdAndContractId(simCardId, contractId)
            .orElseThrow(() -> new NotFoundException("No SIM card with id " + simCardId));

    SimCardStatus oldStatus = simCard.getStatus();
    SimCardStatus newStatus = request.status();
    if (!oldStatus.canTransitionTo(newStatus)) {
      throw new ConflictException("Cannot transition a SIM Card from " + oldStatus + " to " + newStatus);
    }

    simCard.setStatus(newStatus);
    simCardRepository.save(simCard);

    AuditLog.statusChanged(
        "SimCard", simCard.getId(), oldStatus.name(), newStatus.name(), principal.userId(), principal.tenantId());

    return SimCardResponse.of(simCard);
  }

  private Contract findContract(UUID contractId, AuthenticatedPrincipal principal) {
    return contractRepository
        .findByIdAndTenantId(contractId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No contract with id " + contractId));
  }
}
