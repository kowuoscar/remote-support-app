package com.remotesupport.backend.domain;

/**
 * A Request's lifecycle (spec.md Solution: "Status Submitted -> In Progress -> Completed, with
 * Cancelled as an exception path"). Every value the eventual state machine needs is declared now
 * (tester-request-submission ticket note: "don't make the column an enum so narrow that
 * agent-request-fulfillment can't add In Progress/Completed/Cancelled next") even though this
 * ticket only ever creates a Request in {@code SUBMITTED} — the transition rules themselves
 * ({@code agent-request-fulfillment} ticket) are out of scope here.
 */
public enum RequestStatus {
  SUBMITTED,
  IN_PROGRESS,
  COMPLETED,
  CANCELLED;

  /**
   * Whether moving from this status directly to {@code target} is a valid Request transition
   * (agent-request-fulfillment ticket AC: "Agent can move a Request from Submitted to In
   * Progress, and from In Progress to Completed" / "Agent can cancel a Request"). {@code
   * COMPLETED} and {@code CANCELLED} are both terminal — mirrors {@code SmartphoneStatus}'s
   * {@code RETIRED}.
   */
  public boolean canTransitionTo(RequestStatus target) {
    return switch (this) {
      case SUBMITTED -> target == IN_PROGRESS || target == CANCELLED;
      case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
      case COMPLETED, CANCELLED -> false;
    };
  }
}
