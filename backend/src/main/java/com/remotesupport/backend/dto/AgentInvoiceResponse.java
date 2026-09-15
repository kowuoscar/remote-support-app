package com.remotesupport.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An Agent's monthly invoice, all four line items embedded together (spec.md Solution's Agent
 * Invoice entity; agent-standing-amounts-and-invoice-generation ticket AC: scannable at a glance,
 * mirroring {@link ClientInvoiceResponse}). While {@code status} is {@code DRAFT}, every line is
 * computed live by {@code AgentInvoiceController} on every request; from {@code SENT} onward they
 * are read from the frozen snapshot instead (agent-invoice-submission-and-approval ticket) — the
 * shape is identical either way, only where the backend sourced the numbers changes, mirroring
 * {@link ClientInvoiceResponse}'s own live/frozen split. {@code sentAt}/{@code approvedAt}/{@code
 * paidAt} are {@code null} until each transition happens.
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
    BigDecimal totalAmount,
    Instant sentAt,
    Instant approvedAt,
    Instant paidAt) {}
