package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Per-resource Client Invoice authorization (client-invoice-generation ticket AC: "only the
 * Contract's Agent can build/view its draft"; spec.md Access control gives the Manager
 * tenant-wide oversight parity with every other resource). Same "Manager: any Contract, Agent:
 * only their own" shape as {@link FleetAccessGuard#requireCanChangeStatus} and {@link
 * RequestAccessGuard#requireCanChangeStatus} — reused rather than re-derived.
 *
 * <p>Deliberately has no Tester branch at all, unlike {@link FleetAccessGuard#requireCanView}: a
 * draft Client Invoice must stay invisible to a Tester even after this ticket (AC: "A Tester
 * should NOT be able to see a draft Client Invoice at all") — visibility only opens once the
 * invoice is {@code sent}, which is client-invoice-submission-and-visibility's concern, not this
 * one's. A Tester hitting this guard falls into the {@code default} branch and is rejected, the
 * same shape as {@link FleetAccessGuard#requireCanChangeStatus}.
 */
@Component
public class ClientInvoiceAccessGuard {

  private final CallerIdentityResolver callerIdentityResolver;

  public ClientInvoiceAccessGuard(CallerIdentityResolver callerIdentityResolver) {
    this.callerIdentityResolver = callerIdentityResolver;
  }

  public void requireCanBuildOrView(Contract contract, AuthenticatedPrincipal principal) {
    switch (principal.role()) {
      case "MANAGER" -> {}
      case "AGENT" -> requireOwnsContractAsAgent(contract, principal);
      default -> throw new AccessDeniedException("Not allowed to view this Contract's Client Invoice");
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
}
