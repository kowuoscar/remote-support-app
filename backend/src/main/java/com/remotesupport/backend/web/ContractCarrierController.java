package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.dto.CarrierCatalogResponse;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.security.FleetAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Contract-scoped read of its own Country's active Carrier catalog (request-types-and-flow
 * spec: "Anyone who can view a Contract can read the active Carrier catalog of that Contract's
 * Country through a Contract-scoped read. The Country-scoped catalog routes stay Agent and
 * Manager only."; reboot-and-topup-details ticket). This is how a Tester's submit dialog — who
 * has no Country of their own and no access to {@link CarrierController}'s {@code /api/carriers}
 * — gets the Topup Option picker's data, scoped the same way Requests/Fleet already are: {@link
 * FleetAccessGuard#requireCanView} (Manager: any Contract; Agent/Tester: only their own).
 * Archived entries are never included here — a Tester only ever needs what's pickable.
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/carriers")
public class ContractCarrierController {

  private final ContractRepository contractRepository;
  private final FleetAccessGuard fleetAccessGuard;
  private final CarrierCatalogService carrierCatalogService;

  public ContractCarrierController(
      ContractRepository contractRepository,
      FleetAccessGuard fleetAccessGuard,
      CarrierCatalogService carrierCatalogService) {
    this.contractRepository = contractRepository;
    this.fleetAccessGuard = fleetAccessGuard;
    this.carrierCatalogService = carrierCatalogService;
  }

  @GetMapping
  public CarrierCatalogResponse list(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract =
        contractRepository
            .findByIdAndTenantId(contractId, principal.tenantId())
            .orElseThrow(() -> new NotFoundException("No contract with id " + contractId));
    fleetAccessGuard.requireCanView(contract, principal);
    return carrierCatalogService.buildCatalog(
        principal.tenantId(), contract.getAgent().getCountry(), false);
  }
}
