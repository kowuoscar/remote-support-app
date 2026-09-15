package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Per-resource Agent Invoice authorization (agent-standing-amounts-and-invoice-generation ticket
 * AC: "Only the invoice's own Agent can view/build their draft Agent Invoice"; spec.md Access
 * control's Manager oversight-parity rule — same shape {@link ClientInvoiceAccessGuard} already
 * established for Client Invoices). No Tester branch at all: a Client Invoice is visible to
 * Testers once sent, but an Agent Invoice never is (spec.md Access control names only Manager and
 * the Agent themselves).
 */
@Component
public class AgentInvoiceAccessGuard {

  private final CallerIdentityResolver callerIdentityResolver;

  public AgentInvoiceAccessGuard(CallerIdentityResolver callerIdentityResolver) {
    this.callerIdentityResolver = callerIdentityResolver;
  }

  public void requireCanBuildOrView(Agent agent, AuthenticatedPrincipal principal) {
    switch (principal.role()) {
      case "MANAGER" -> {}
      case "AGENT" -> {
        boolean owns =
            callerIdentityResolver
                .resolveAgentId(principal)
                .map(agentId -> agentId.equals(agent.getId()))
                .orElse(false);
        if (!owns) {
          throw new AccessDeniedException("Not your Agent Invoice");
        }
      }
      default -> throw new AccessDeniedException("Not allowed to view this Agent Invoice");
    }
  }
}
