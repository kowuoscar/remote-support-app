package com.remotesupport.backend.domain;

/**
 * A Client Invoice's lifecycle (spec.md Solution: "Lifecycle: draft (the Agent is assembling it)
 * -> sent (submitted by the Agent) -> approved (locked by the Manager)"). {@code DRAFT} was the
 * only value the client-invoice-generation ticket ever wrote; this ticket
 * (client-invoice-submission-and-visibility) adds the {@code SENT}/{@code APPROVED} transitions
 * themselves.
 */
public enum ClientInvoiceStatus {
  DRAFT,
  SENT,
  APPROVED;

  /**
   * Whether moving from this status directly to {@code target} is a valid Client Invoice
   * transition (ticket AC: "Agent can send a draft Client Invoice, moving it to status sent" /
   * "Manager can approve it, moving it to status approved" / "Manager cannot approve a Client
   * Invoice still in draft"). Both {@code DRAFT -> SENT} (the Agent's send) and {@code SENT ->
   * APPROVED} (the Manager's approval) are single forward steps with no way back — there is no
   * "un-send" or "un-approve" in spec.md's lifecycle. Mirrors {@link RequestStatus#canTransitionTo}
   * and {@link SmartphoneStatus}'s shape: {@code APPROVED} is terminal, exactly like {@code
   * RequestStatus.COMPLETED}/{@code CANCELLED}.
   */
  public boolean canTransitionTo(ClientInvoiceStatus target) {
    return switch (this) {
      case DRAFT -> target == SENT;
      case SENT -> target == APPROVED;
      case APPROVED -> false;
    };
  }
}
