package com.remotesupport.backend.dto;

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
 */
public record ClientInvoiceResponse(
    UUID id,
    UUID contractId,
    LocalDate billingMonth,
    String status,
    String currency,
    BigDecimal baseAmount,
    List<FeeResponse> feeLines,
    BigDecimal totalAmount,
    List<CarrierInvoiceFileResponse> files,
    Instant sentAt,
    Instant approvedAt) {}
