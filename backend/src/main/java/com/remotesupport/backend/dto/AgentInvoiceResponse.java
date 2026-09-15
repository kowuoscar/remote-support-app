package com.remotesupport.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An Agent's monthly invoice, all four line items embedded together (spec.md Solution's Agent
 * Invoice entity; agent-standing-amounts-and-invoice-generation ticket AC: scannable at a glance,
 * mirroring {@link ClientInvoiceResponse}). Every line is computed live by {@code
 * AgentInvoiceController} on every request — this ticket only ever writes {@code DRAFT}, so there
 * is nothing to freeze yet (unlike {@link ClientInvoiceResponse}, which already has a frozen/live
 * split from the ticket before this one).
 *
 * <p>{@code rolloutAdvanceRepayment} is negative (or zero — see {@code StandingAmountService}),
 * {@code rolloutAdvanceNewAdvance} is positive (or zero); {@code totalAmount} is the sum of all
 * four lines.
 */
public record AgentInvoiceResponse(
    UUID id,
    UUID agentId,
    LocalDate billingMonth,
    String status,
    String currency,
    BigDecimal localSupportFees,
    BigDecimal salary,
    BigDecimal rolloutAdvanceRepayment,
    BigDecimal rolloutAdvanceNewAdvance,
    BigDecimal totalAmount) {}
