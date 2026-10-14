package com.remotesupport.backend.repository;

/**
 * The one JPQL expression for an Agent Invoice's frozen total (ADR 0003): its four snapshotted
 * figures, after any Manager override. The Review Queue and the Tenant totals both build their
 * Agent Invoice amount from it, so a change to what an Agent Invoice totals (the invoice-adjustment
 * spec's fifth figure) is made here and nowhere else. Meant for the alias {@code ai}.
 */
final class AgentInvoiceTotal {

  static final String SNAPSHOT =
      "ai.snapshotLocalSupportFees + ai.snapshotSalary"
          + " + ai.snapshotRolloutAdvanceRepayment + ai.snapshotRolloutAdvanceNewAdvance";

  private AgentInvoiceTotal() {}
}
