package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.Tester;
import com.remotesupport.backend.dto.RequestCreateRequest;
import com.remotesupport.backend.dto.RequestResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.RequestRepository;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.security.FleetAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.security.RequestAccessGuard;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Requests raised against one Contract (tester-request-submission ticket). Create is Tester-only,
 * enforced at the request-matcher level in {@link com.remotesupport.backend.security.SecurityConfig}
 * and re-checked (with Contract ownership) by {@link RequestAccessGuard}; viewing is scoped
 * per-Contract exactly like Fleet (Manager: any; Agent/Tester: only their own), so it reuses
 * {@link FleetAccessGuard#requireCanView} directly. A Tester's "see every Request raised by
 * anyone at my Client" visibility (spec.md user story 31) is a frontend concern: the client
 * fetches this same per-Contract endpoint once per Contract it holds and flattens the results,
 * exactly like the Fleet views already do (fleet-management ticket) — there is no separate
 * Client-wide endpoint.
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/requests")
public class RequestController {

  private final ContractRepository contractRepository;
  private final RequestRepository requestRepository;
  private final TesterRepository testerRepository;
  private final FleetAccessGuard fleetAccessGuard;
  private final RequestAccessGuard requestAccessGuard;

  public RequestController(
      ContractRepository contractRepository,
      RequestRepository requestRepository,
      TesterRepository testerRepository,
      FleetAccessGuard fleetAccessGuard,
      RequestAccessGuard requestAccessGuard) {
    this.contractRepository = contractRepository;
    this.requestRepository = requestRepository;
    this.testerRepository = testerRepository;
    this.fleetAccessGuard = fleetAccessGuard;
    this.requestAccessGuard = requestAccessGuard;
  }

  @PostMapping
  public ResponseEntity<RequestResponse> create(
      @PathVariable UUID contractId,
      @Valid @RequestBody RequestCreateRequest requestBody,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    requestAccessGuard.requireCanSubmit(contract, principal);

    Tester tester =
        testerRepository
            .findByUserId(principal.userId())
            .orElseThrow(() -> new AccessDeniedException("No Tester profile for this login"));

    Request request = new Request();
    request.setId(UUID.randomUUID());
    request.setTenant(contract.getTenant());
    request.setContract(contract);
    request.setTester(tester);
    request.setType(requestBody.type());
    request.setStatus(RequestStatus.SUBMITTED);
    request.setCreatedAt(Instant.now());
    requestRepository.save(request);

    AuditLog.requestSubmitted(
        request.getId(),
        contract.getId(),
        request.getType().name(),
        principal.userId(),
        principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED).body(RequestResponse.of(request));
  }

  @GetMapping
  public List<RequestResponse> list(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    fleetAccessGuard.requireCanView(contract, principal);

    return requestRepository.findByContractIdOrderByCreatedAtAsc(contractId).stream()
        .map(RequestResponse::of)
        .toList();
  }

  private Contract findContract(UUID contractId, AuthenticatedPrincipal principal) {
    return contractRepository
        .findByIdAndTenantId(contractId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No contract with id " + contractId));
  }
}
