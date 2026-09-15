package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Per-resource Request authorization (tester-request-submission ticket AC: "A Tester cannot
 * submit a Request against another Client's Contract"). Viewing a Contract's Requests is scoped
 * identically to Fleet visibility (Manager: any Contract; Agent/Tester: only their own), so
 * {@link com.remotesupport.backend.web.RequestController} reuses {@link FleetAccessGuard#requireCanView}
 * directly rather than duplicating it here; only Request's submission rule — Tester-only, and
 * only against their own Client's Contract — is specific enough to warrant its own check.
 */
@Component
public class RequestAccessGuard {

  private final CallerIdentityResolver callerIdentityResolver;

  public RequestAccessGuard(CallerIdentityResolver callerIdentityResolver) {
    this.callerIdentityResolver = callerIdentityResolver;
  }

  /**
   * Only a Tester may submit a Request (spec.md Access control: "Tester: create/read Requests
   * within their Client's Contracts"), and only against a Contract belonging to their own Client.
   */
  public void requireCanSubmit(Contract contract, AuthenticatedPrincipal principal) {
    if (!"TESTER".equals(principal.role())) {
      throw new AccessDeniedException("Only a Tester can submit a Request");
    }
    boolean owns =
        callerIdentityResolver
            .resolveClientId(principal)
            .map(clientId -> clientId.equals(contract.getClient().getId()))
            .orElse(false);
    if (!owns) {
      throw new AccessDeniedException("Not your Client's Contract");
    }
  }

  /**
   * Only the Contract's own Agent (or a Manager, for tenant-wide oversight — same shape as
   * {@link FleetAccessGuard#requireCanChangeStatus}) may change a Request's status
   * (agent-request-fulfillment ticket AC: "Only the Contract's Agent can change that Request's
   * status; a Tester cannot change status"). A Tester or any other Agent's Contract is rejected.
   */
  public void requireCanChangeStatus(Contract contract, AuthenticatedPrincipal principal) {
    switch (principal.role()) {
      case "MANAGER" -> {}
      case "AGENT" -> requireOwnsContractAsAgent(contract, principal);
      default -> throw new AccessDeniedException("Not allowed to change this Request's status");
    }
  }

  /**
   * Only the Contract's own Agent (or a Manager) may log a Request proactively on a Tester's
   * behalf (agent-request-fulfillment ticket AC: "Agent can log a Request directly ... for one of
   * their own Contracts").
   */
  public void requireCanLogProactively(Contract contract, AuthenticatedPrincipal principal) {
    switch (principal.role()) {
      case "MANAGER" -> {}
      case "AGENT" -> requireOwnsContractAsAgent(contract, principal);
      default -> throw new AccessDeniedException("Not allowed to log a Request on this Contract");
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
