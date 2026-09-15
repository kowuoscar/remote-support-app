package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Per-resource Fleet authorization (fleet-management ticket AC: "An Agent cannot view or modify
 * Fleet on a Contract that isn't theirs; a Tester cannot view Fleet outside their own Client's
 * Contracts"). {@link com.remotesupport.backend.security.SecurityConfig} can only express
 * role-level rules from the URL alone; whether a given Contract belongs to the caller is a
 * per-request check, so it lives here instead — thrown as Spring Security's own
 * {@link AccessDeniedException} so it's handled exactly like every other 403 in the app (see
 * SecurityConfig's note on the {@code /error}-forward pitfall).
 */
@Component
public class FleetAccessGuard {

  private final CallerIdentityResolver callerIdentityResolver;

  public FleetAccessGuard(CallerIdentityResolver callerIdentityResolver) {
    this.callerIdentityResolver = callerIdentityResolver;
  }

  /** Manager: any Contract in the tenant. Agent/Tester: only a Contract that is theirs. */
  public void requireCanView(Contract contract, AuthenticatedPrincipal principal) {
    switch (principal.role()) {
      case "MANAGER" -> {}
      case "AGENT" -> requireOwnsContractAsAgent(contract, principal);
      case "TESTER" -> requireOwnsContractAsTester(contract, principal);
      default -> throw new AccessDeniedException("Not allowed to view this Contract's Fleet");
    }
  }

  /**
   * Manager or the Contract's own Agent may change Fleet status (spec.md Access control: Manager
   * has full CRUD on Fleet, Agent has full CRUD on Fleet status within their own Contracts); a
   * Tester never may.
   */
  public void requireCanChangeStatus(Contract contract, AuthenticatedPrincipal principal) {
    switch (principal.role()) {
      case "MANAGER" -> {}
      case "AGENT" -> requireOwnsContractAsAgent(contract, principal);
      default -> throw new AccessDeniedException("Not allowed to change this Contract's Fleet status");
    }
  }

  private void requireOwnsContractAsAgent(Contract contract, AuthenticatedPrincipal principal) {
    boolean owns =
        callerIdentityResolver
            .resolveAgentId(principal)
            .map(agentId -> agentId.equals(contract.getAgent().getId()))
            .orElse(false);
    if (!owns) {
      throw new AccessDeniedException("Not your Contract");
    }
  }

  private void requireOwnsContractAsTester(Contract contract, AuthenticatedPrincipal principal) {
    boolean owns =
        callerIdentityResolver
            .resolveClientId(principal)
            .map(clientId -> clientId.equals(contract.getClient().getId()))
            .orElse(false);
    if (!owns) {
      throw new AccessDeniedException("Not your Client's Contract");
    }
  }
}
