package com.remotesupport.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A Contract's Client Invoice for one billing month, with its base amount, Fee lines and attached
 * files all embedded together — client-invoice-generation ticket AC: "The draft view is scannable
 * at a glance: base amount, each Fee line, and attached files are all visible together". {@code
 * baseAmount}/{@code feeLines}/{@code totalAmount} are computed fresh by {@code
 * ClientInvoiceController} on every request, never read off the {@code ClientInvoice} entity
 * itself (see its Javadoc).
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
    List<CarrierInvoiceFileResponse> files) {}
