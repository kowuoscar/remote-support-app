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
  CANCELLED
}
