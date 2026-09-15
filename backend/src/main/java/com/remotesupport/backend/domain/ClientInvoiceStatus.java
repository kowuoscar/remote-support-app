package com.remotesupport.backend.domain;

/**
 * A Client Invoice's lifecycle (spec.md Solution: "Lifecycle: draft (the Agent is assembling it)
 * -> sent (submitted by the Agent) -> approved (locked by the Manager)"). Every value the
 * eventual state machine needs is declared now (client-invoice-generation ticket note: mirrors
 * {@link RequestStatus}'s pre-declaration — declare the enum wide enough for
 * client-invoice-submission-and-visibility's {@code sent}/{@code approved} transitions without a
 * schema change) even though this ticket only ever writes {@code DRAFT}; the transition rules
 * themselves (send, approve) are that later ticket's concern, out of scope here.
 */
public enum ClientInvoiceStatus {
  DRAFT,
  SENT,
  APPROVED
}
