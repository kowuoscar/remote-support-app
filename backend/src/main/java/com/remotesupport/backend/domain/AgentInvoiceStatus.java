package com.remotesupport.backend.domain;

/**
 * An Agent Invoice's lifecycle (spec.md Solution: "Lifecycle: draft -> sent -> approved -> paid,
 * every transition set manually by the Manager"; agent-invoice-submission-and-approval ticket
 * completes it — agent-standing-amounts-and-invoice-generation only ever wrote {@code DRAFT}).
 */
public enum AgentInvoiceStatus {
  DRAFT,
  SENT,
  APPROVED,
  PAID;

  /**
   * Whether moving from this status directly to {@code target} is a valid Agent Invoice
   * transition (ticket AC: "Agent can send their draft Agent Invoice, moving it to status sent"
   * / "Manager can approve a sent Agent Invoice" / "Manager can mark an approved Agent Invoice as
   * paid"). Every step is a single forward move with no way back — {@code PAID} is terminal,
   * exactly like {@link ClientInvoiceStatus#APPROVED}, just one step further down the same shape.
   */
  public boolean canTransitionTo(AgentInvoiceStatus target) {
    return switch (this) {
      case DRAFT -> target == SENT;
      case SENT -> target == APPROVED;
      case APPROVED -> target == PAID;
      case PAID -> false;
    };
  }
}
