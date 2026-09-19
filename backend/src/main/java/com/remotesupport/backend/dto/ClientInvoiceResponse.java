package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A Contract's Client Invoice for one billing month, with its base amount, Fee lines and attached
 * files all embedded together — client-invoice-generation ticket AC: "The draft view is scannable
 * at a glance: base amount, each Fee line, and attached files are all visible together". While
 * {@code status} is {@code DRAFT}, {@code baseAmount}/{@code feeLines}/{@code totalAmount} are
 * computed fresh by {@code ClientInvoiceController} on every request; from {@code SENT} onward
 * they are read from the frozen snapshot instead (client-invoice-submission-and-visibility
 * ticket) — never off the {@code ClientInvoice} entity directly either way. {@code sentAt}/{@code
 * approvedAt} are {@code null} until each transition happens.
 *
 * <p>{@code basePostpaidSims} (cancelled-sim-billed-through-its-month ticket) is the live
 * breakdown behind {@code baseAmount}: every Postpaid SIM Card counted this billing month,
 * including one still billing because it was cancelled on or after the month's first day (spec.md
 * Solution, "Billing a cancelled Postpaid SIM"). {@code null} once the invoice is {@code
 * SENT}/{@code APPROVED} — there is no per-unit breakdown of the frozen {@code
 * snapshotBaseAmount} to serve (ADR 0001).
 */
public record ClientInvoiceResponse(
    UUID id,
    UUID contractId,
    LocalDate billingMonth,
    String status,
    String currency,
    BigDecimal baseAmount,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<ClientInvoiceBaseSimLineResponse> basePostpaidSims,
    List<FeeResponse> feeLines,
    BigDecimal totalAmount,
    List<CarrierInvoiceFileResponse> files,
    Instant sentAt,
    Instant approvedAt) {}
