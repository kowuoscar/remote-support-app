package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Per-Country Carrier catalog authorization (carrier-catalog spec, Access): the Company Manager
 * reads and edits every Country's catalog; an Agent only their own Country's; a Tester none (also
 * refused at the matcher level in {@link SecurityConfig}). Whether a Country is the caller's own
 * can't be read off the URL, so it lives here — thrown as Spring Security's own
 * {@link AccessDeniedException}, like {@link FleetAccessGuard}.
 */
@Component
public class CarrierCatalogAccessGuard {

  private final CallerIdentityResolver callerIdentityResolver;
  private final AgentRepository agentRepository;

  public CarrierCatalogAccessGuard(
      CallerIdentityResolver callerIdentityResolver, AgentRepository agentRepository) {
    this.callerIdentityResolver = callerIdentityResolver;
    this.agentRepository = agentRepository;
  }

  /**
   * The Country whose catalog the caller works on. An Agent who names no Country gets their own;
   * naming another is refused. The Manager gets the Country they named, or {@code null} when they
   * named none.
   */
  public Country requireCanUse(Country requested, AuthenticatedPrincipal principal) {
    switch (principal.role()) {
      case "MANAGER" -> {
        return requested;
      }
      case "AGENT" -> {
        Country own =
            callerIdentityResolver
                .resolveAgentId(principal)
                .flatMap(agentRepository::findById)
                .map(Agent::getCountry)
                .orElseThrow(() -> new AccessDeniedException("No Agent behind this login"));
        if (requested != null && requested != own) {
          throw new AccessDeniedException("Not your Country's catalog");
        }
        return own;
      }
      default -> throw new AccessDeniedException("No access to the Carrier catalog");
    }
  }
}
