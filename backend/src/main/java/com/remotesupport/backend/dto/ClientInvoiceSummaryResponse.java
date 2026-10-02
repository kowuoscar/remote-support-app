package com.remotesupport.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A sent or approved Client Invoice reduced to what a Tester's dashboard shows: no Fee lines, no
 * files and no send-back fields. {@code totalAmount} is the invoice's frozen total (ADR 0001).
 */
public record ClientInvoiceSummaryResponse(
    UUID contractId,
    UUID invoiceId,
    LocalDate billingMonth,
    String status,
    String currency,
    BigDecimal totalAmount) {}
