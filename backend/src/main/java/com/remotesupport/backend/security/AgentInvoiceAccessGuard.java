package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Per-resource Agent Invoice authorization (agent-standing-amounts-and-invoice-generation ticket
 * AC: "Only the invoice's own Agent can view/build their draft Agent Invoice"; completed by
 * agent-invoice-submission-and-approval's send/override/approve/paid actions; spec.md Access
 * control's Manager oversight-parity rule — same shape {@link ClientInvoiceAccessGuard} already
 * established for Client Invoices). No Tester branch anywhere in this class: a Client Invoice is
 * visible to Testers once sent, but an Agent Invoice never is, at any status (spec.md Access
 * control names only Manager and the Agent themselves).
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
      case "AGENT" -> requireOwnsAgent(agent, principal);
      default -> throw new AccessDeniedException("Not allowed to view this Agent Invoice");
    }
  }

  /**
   * Only the invoice's own Agent may send it (ticket AC: "Agent can send their draft Agent
   * Invoice"; spec.md Access control: sending is the Agent's act of submitting their own work) —
   * deliberately narrower than {@link #requireCanBuildOrView}: a Manager can view/build a draft
   * (oversight parity) but never sends one themselves, mirroring {@link
   * ClientInvoiceAccessGuard#requireCanSend}.
   */
  public void requireCanSend(Agent agent, AuthenticatedPrincipal principal) {
    if (!"AGENT".equals(principal.role())) {
      throw new AccessDeniedException("Only this Agent Invoice's own Agent can send it");
    }
    requireOwnsAgent(agent, principal);
  }

  /**
   * Only a Manager may override a sent Agent Invoice's Salary/Rollout Advance line (ticket AC;
   * spec.md user story 10: "As a Company Manager, I want to override the salary or Rollout
   * Advance line on one specific Agent Invoice").
   */
  public void requireCanOverride(AuthenticatedPrincipal principal) {
    if (!"MANAGER".equals(principal.role())) {
      throw new AccessDeniedException("Only a Manager can override an Agent Invoice line");
    }
  }

  /** Only a Manager may approve an Agent Invoice (ticket AC; spec.md Access control). */
  public void requireCanApprove(AuthenticatedPrincipal principal) {
    if (!"MANAGER".equals(principal.role())) {
      throw new AccessDeniedException("Only a Manager can approve an Agent Invoice");
    }
  }

  /** Only a Manager may mark an Agent Invoice paid (ticket AC; spec.md Access control). */
  public void requireCanMarkPaid(AuthenticatedPrincipal principal) {
    if (!"MANAGER".equals(principal.role())) {
      throw new AccessDeniedException("Only a Manager can mark an Agent Invoice as paid");
    }
  }

  private void requireOwnsAgent(Agent agent, AuthenticatedPrincipal principal) {
    boolean owns =
        callerIdentityResolver
            .resolveAgentId(principal)
            .map(agentId -> agentId.equals(agent.getId()))
            .orElse(false);
    if (!owns) {
      throw new AccessDeniedException("Not your Agent Invoice");
    }
  }
}
