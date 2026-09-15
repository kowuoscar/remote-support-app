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
}
