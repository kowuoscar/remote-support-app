package com.remotesupport.backend.domain;

/**
 * A Request's lifecycle (spec.md Solution: "Status Submitted -> In Progress -> Completed, with
 * Cancelled as an exception path"). Every value the eventual state machine needs is declared now
 * (tester-request-submission ticket note: "don't make the column an enum so narrow that
 * agent-request-fulfillment can't add In Progress/Completed/Cancelled next") even though this
 * ticket only ever creates a Request in {@code SUBMITTED} — the transition rules themselves
 * ({@code agent-request-fulfillment} ticket) are out of scope here.
 *
 * <p>{@code PENDING_APPROVAL}/{@code REJECTED} (request-types-and-flow spec, Lifecycle;
 * manager-approves-requests ticket): the starting status of every approval-required Request —
 * Provision Smartphone, Provision SIM, Replace Smartphone, Replace SIM (see {@link
 * RequestType#requiresApproval()}) — whoever raised it. Only the Company Manager moves it on,
 * through the dedicated approve/reject actions ({@code RequestByIdController}), never through the
 * general status-change PATCH: {@code PENDING_APPROVAL -> CANCELLED} is the one transition that
 * route still allows, for either the Contract's Agent or a Manager (CONTEXT.md "Pending Approval",
 * "Rejected").
 */
public enum RequestStatus {
  PENDING_APPROVAL,
  SUBMITTED,
  IN_PROGRESS,
  COMPLETED,
  CANCELLED,
  REJECTED;

  /**
   * Whether moving from this status directly to {@code target} is a valid Request transition
   * (agent-request-fulfillment ticket AC: "Agent can move a Request from Submitted to In
   * Progress, and from In Progress to Completed" / "Agent can cancel a Request"). {@code
   * COMPLETED}, {@code CANCELLED} and {@code REJECTED} are all terminal — mirrors {@code
   * SmartphoneStatus}'s {@code RETIRED}. Approving/rejecting from {@code PENDING_APPROVAL} are
   * valid states of the overall lifecycle, but only the dedicated approve/reject actions may take
   * them — the general status-change endpoint additionally refuses to use them (see {@code
   * RequestController#updateStatus}).
   */
  public boolean canTransitionTo(RequestStatus target) {
    return switch (this) {
      case PENDING_APPROVAL -> target == SUBMITTED || target == REJECTED || target == CANCELLED;
      case SUBMITTED -> target == IN_PROGRESS || target == CANCELLED;
      case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
      case COMPLETED, CANCELLED, REJECTED -> false;
    };
  }
}
