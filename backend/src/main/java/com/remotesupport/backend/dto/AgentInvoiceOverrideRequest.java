package com.remotesupport.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/**
 * A Manager's per-invoice override of a {@code sent} Agent Invoice's Salary and/or Rollout
 * Advance line, applied at approval-review time (spec.md user story 10; agent-invoice-submission-
 * and-approval ticket AC: "Manager can override the Salary or Rollout Advance value on that one
 * invoice at approval time, without changing the Agent's standing amount used by future
 * invoices"). Both fields are optional — {@code AgentInvoiceController#override} rejects a request
 * with neither set (400) — so a Manager can override just Salary, just the Rollout Advance, or
 * both in one call.
 *
 * <p>Only the <b>new advance</b> line is exposed here, never the repayment line: the repayment
 * simply settles an amount already fixed and communicated on this same Agent's prior invoice, not
 * a decision the Manager is making now, whereas the new advance is the forward-looking amount for
 * next month's cash-flow support the Manager is actively reviewing this invoice to approve — see
 * CONTEXT.md's "Agent Invoice" entry for the full reasoning.
 */
public record AgentInvoiceOverrideRequest(
    @DecimalMin(value = "0.0", inclusive = true) BigDecimal salary,
    @DecimalMin(value = "0.0", inclusive = true) BigDecimal rolloutAdvanceNewAdvance) {}
