package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Disposition;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.dto.StockUnitResponse;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.ReturnedUnitRepository;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.security.CallerIdentityResolver;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Agent Stock read (returns-and-agent-stock spec, Solution's Agent Stock; agent-stock ticket
 * AC: "The Agent sees their own Stock ... the Manager sees every Agent's, filterable by Agent").
 * Manager and Agent only at the matcher level (SecurityConfig) — a Tester gets 403 without a
 * per-request check, since Stock has no per-Contract ownership shape to check the way Fleet does.
 * Read-only: a unit enters Stock only through a completed Return ({@code ReturnCompletionEffect})
 * and leaves only through Stock fulfilment (a later ticket) — nothing here writes.
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

  private final SmartphoneRepository smartphoneRepository;
  private final SimCardRepository simCardRepository;
  private final AgentRepository agentRepository;
  private final ReturnedUnitRepository returnedUnitRepository;
  private final CallerIdentityResolver callerIdentityResolver;

  public StockController(
      SmartphoneRepository smartphoneRepository,
      SimCardRepository simCardRepository,
      AgentRepository agentRepository,
      ReturnedUnitRepository returnedUnitRepository,
      CallerIdentityResolver callerIdentityResolver) {
    this.smartphoneRepository = smartphoneRepository;
    this.simCardRepository = simCardRepository;
    this.agentRepository = agentRepository;
    this.returnedUnitRepository = returnedUnitRepository;
    this.callerIdentityResolver = callerIdentityResolver;
  }

  @GetMapping
  public List<StockUnitResponse> list(
      @RequestParam(required = false) UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    UUID tenantId = principal.tenantId();
    UUID scopeAgentId = resolveScopeAgentId(agentId, principal);

    List<Smartphone> smartphones =
        scopeAgentId != null
            ? smartphoneRepository.findByTenantIdAndHoldingAgentIdOrderByCreatedAtAsc(tenantId, scopeAgentId)
            : smartphoneRepository.findByTenantIdAndHoldingAgentIdIsNotNullOrderByCreatedAtAsc(tenantId);
    List<SimCard> simCards =
        scopeAgentId != null
            ? simCardRepository.findByTenantIdAndHoldingAgentIdOrderByCreatedAtAsc(tenantId, scopeAgentId)
            : simCardRepository.findByTenantIdAndHoldingAgentIdIsNotNullOrderByCreatedAtAsc(tenantId);

    List<StockUnitResponse> units = new ArrayList<>();
    for (Smartphone smartphone : smartphones) {
      units.add(StockUnitResponse.of(smartphone, originContractOfSmartphone(smartphone.getId())));
    }
    for (SimCard simCard : simCards) {
      units.add(StockUnitResponse.of(simCard, originContractOfSimCard(simCard.getId())));
    }
    return units;
  }

  /**
   * An Agent always sees only their own Stock (ticket AC), ignoring any {@code agentId} they might
   * pass; a Manager sees every Agent's Stock unless they narrow it with {@code agentId} (ticket AC:
   * "filterable by Agent"), which must belong to this tenant. Every other role is refused at the
   * matcher level before this ever runs.
   */
  private UUID resolveScopeAgentId(UUID agentId, AuthenticatedPrincipal principal) {
    if ("AGENT".equals(principal.role())) {
      return callerIdentityResolver
          .resolveAgentId(principal)
          .orElseThrow(() -> new AccessDeniedException("No Agent login linked"));
    }
    if (agentId == null) {
      return null;
    }
    Agent agent =
        agentRepository
            .findByIdAndTenantId(agentId, principal.tenantId())
            .orElseThrow(() -> new NotFoundException("No agent with id " + agentId));
    return agent.getId();
  }

  /**
   * The Contract a Stock unit last left (ticket AC: "the Contract each came from") — its own
   * {@code contract} is null once in Stock, so this looks up the {@link Disposition#KEPT_IN_STOCK}
   * {@code ReturnedUnit} row that put it there instead (see that repository method's own Javadoc).
   */
  private Contract originContractOfSmartphone(UUID smartphoneId) {
    return returnedUnitRepository
        .findFirstBySmartphoneIdAndDispositionOrderByCreatedAtDesc(smartphoneId, Disposition.KEPT_IN_STOCK)
        .map(unit -> unit.getRequest().getContract())
        .orElse(null);
  }

  private Contract originContractOfSimCard(UUID simCardId) {
    return returnedUnitRepository
        .findFirstBySimCardIdAndDispositionOrderByCreatedAtDesc(simCardId, Disposition.KEPT_IN_STOCK)
        .map(unit -> unit.getRequest().getContract())
        .orElse(null);
  }
}
