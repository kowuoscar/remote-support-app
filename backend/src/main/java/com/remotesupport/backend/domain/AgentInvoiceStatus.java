package com.remotesupport.backend.domain;

/**
 * An Agent Invoice's lifecycle (spec.md Solution: "Lifecycle: draft -> sent -> approved -> paid,
 * every transition set manually by the Manager"). Declared wide enough for the whole lifecycle
 * now, even though this ticket (agent-standing-amounts-and-invoice-generation) only ever writes
 * {@code DRAFT} — the next ticket (agent-invoice-submission-and-approval) needs {@code
 * SENT}/{@code APPROVED}/{@code PAID} without a schema or enum change, exactly the same
 * "pre-declare, don't implement yet" shape {@link RequestStatus} and {@link ClientInvoiceStatus}
 * already established. Transition rules ({@code canTransitionTo}) are that next ticket's concern,
 * not this one's.
 */
public enum AgentInvoiceStatus {
  DRAFT,
  SENT,
  APPROVED,
  PAID
}
