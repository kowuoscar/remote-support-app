package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One Client Invoice of an Agent's month as the Manager reviews it, read in a single query with its
 * total summed from the stored lines (every invoice that reached the Manager has them, ADR
 * 0004/0005).
 */
public record ClientInvoiceReviewRow(
    UUID id,
    UUID contractId,
    String clientName,
    ClientInvoiceStatus status,
    Currency currency,
    BigDecimal totalAmount,
    Instant sentAt,
    Instant approvedAt,
    Instant sentBackAt) {}
