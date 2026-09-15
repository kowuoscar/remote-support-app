package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.dto.TesterResponse;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.security.FleetAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only listing of a Contract's Client's Testers, nested under the Contract exactly like
 * Fleet and Requests (agent-request-fulfillment ticket): the Agent needs this to pick which
 * Tester a proactively-logged Request is raised on behalf of, without reaching for the
 * Manager-only {@code /api/clients/{clientId}/testers} endpoint ({@link
 * com.remotesupport.backend.security.SecurityConfig} keeps that Manager-only). Scoped with {@link
 * FleetAccessGuard#requireCanView} — the same visibility rule as everything else nested under a
 * Contract.
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/testers")
public class ContractTestersController {

  private final ContractRepository contractRepository;
  private final TesterRepository testerRepository;
  private final FleetAccessGuard fleetAccessGuard;

  public ContractTestersController(
      ContractRepository contractRepository,
      TesterRepository testerRepository,
      FleetAccessGuard fleetAccessGuard) {
    this.contractRepository = contractRepository;
    this.testerRepository = testerRepository;
    this.fleetAccessGuard = fleetAccessGuard;
  }

  @GetMapping
  public List<TesterResponse> list(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract =
        contractRepository
            .findByIdAndTenantId(contractId, principal.tenantId())
            .orElseThrow(() -> new NotFoundException("No contract with id " + contractId));
    fleetAccessGuard.requireCanView(contract, principal);

    return testerRepository.findByClientIdOrderByCreatedAtAsc(contract.getClient().getId()).stream()
        .map(TesterResponse::of)
        .toList();
  }
}
