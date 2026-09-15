package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Per-resource Client Invoice authorization (client-invoice-generation ticket AC: "only the
 * Contract's Agent can build/view its draft"; client-invoice-submission-and-visibility ticket:
 * send, Client visibility, Manager approval; spec.md Access control). Same "Manager: any
 * Contract, Agent: only their own" shape as {@link FleetAccessGuard#requireCanChangeStatus} and
 * {@link RequestAccessGuard#requireCanChangeStatus} — reused rather than re-derived.
 */
@Component
public class ClientInvoiceAccessGuard {

  private final CallerIdentityResolver callerIdentityResolver;

  public ClientInvoiceAccessGuard(CallerIdentityResolver callerIdentityResolver) {
    this.callerIdentityResolver = callerIdentityResolver;
  }

  /**
   * Manager or the Contract's own Agent — used for the draft-building surface (viewing/creating
   * the draft, attaching carrier invoice files). Deliberately has no Tester branch: a Tester's
   * visibility is gated on the invoice's *status*, not just Contract ownership, and only ever
   * read-only, so it goes through {@link #requireCanView} instead, never this method.
   */
  public void requireCanBuildOrView(Contract contract, AuthenticatedPrincipal principal) {
    switch (principal.role()) {
      case "MANAGER" -> {}
      case "AGENT" -> requireOwnsContractAsAgent(contract, principal);
      default -> throw new AccessDeniedException("Not allowed to view this Contract's Client Invoice");
    }
  }

  /**
   * Only the Contract's own Agent may send its Client Invoice (ticket AC; spec.md Access control:
   * sending is the Agent's act of submitting their own work) — deliberately narrower than {@link
   * #requireCanBuildOrView}: a Manager can view/build a draft (oversight parity with every other
   * resource) but never sends one themselves.
   */
  public void requireCanSend(Contract contract, AuthenticatedPrincipal principal) {
    if (!"AGENT".equals(principal.role())) {
      throw new AccessDeniedException("Only this Contract's Agent can send its Client Invoice");
    }
    requireOwnsContractAsAgent(contract, principal);
  }

  /**
   * Read access to a Client Invoice once it is visible to the caller's role (ticket AC: "Once
   * sent, every Tester at that Contract's Client can view the Client Invoice read-only" / "A
   * draft Client Invoice is never visible to a Tester"). Manager: any Contract, any status. Agent:
   * only their own Contract, any status (an Agent can still see their own draft — unchanged from
   * {@link #requireCanBuildOrView}). Tester: only their own Client's Contract, and only once
   * {@code status} is no longer {@code DRAFT} — checked with the exact same {@link
   * AccessDeniedException} (403) regardless of whether the invoice doesn't exist yet or exists but
   * is still a draft, so a Tester can never distinguish "no invoice yet" from "there's a draft I'm
   * not allowed to see" from the response alone.
   */
  public void requireCanView(Contract contract, ClientInvoiceStatus status, AuthenticatedPrincipal principal) {
    switch (principal.role()) {
      case "MANAGER" -> {}
      case "AGENT" -> requireOwnsContractAsAgent(contract, principal);
      case "TESTER" -> requireOwnsContractAsTesterAndVisible(contract, status, principal);
      default -> throw new AccessDeniedException("Not allowed to view this Contract's Client Invoice");
    }
  }

  /** Only a Manager may approve a Client Invoice (ticket AC; spec.md Access control). */
  public void requireCanApprove(AuthenticatedPrincipal principal) {
    if (!"MANAGER".equals(principal.role())) {
      throw new AccessDeniedException("Only a Manager can approve a Client Invoice");
    }
  }

  private void requireOwnsContractAsTesterAndVisible(
      Contract contract, ClientInvoiceStatus status, AuthenticatedPrincipal principal) {
    boolean owns =
        callerIdentityResolver
            .resolveClientId(principal)
            .map(clientId -> clientId.equals(contract.getClient().getId()))
            .orElse(false);
    if (!owns || status == ClientInvoiceStatus.DRAFT) {
      throw new AccessDeniedException("Not allowed to view this Contract's Client Invoice");
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
