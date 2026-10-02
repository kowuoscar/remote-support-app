package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One Client Invoice as the Review Queue needs it, read in a single query: its Contract's Client
 * and Agent names, and its total's two parts — the sum of its stored base-amount lines and the sum of
 * its stored Fee lines (zero when it has none). Only meaningful for an invoice
 * that is at least {@code SENT}, since a draft never sent has no stored lines (ADR 0001).
 */
public record ClientInvoiceQueueRow(
    UUID id,
    ClientInvoiceStatus status,
    LocalDate billingMonth,
    UUID contractId,
    String clientName,
    String agentName,
    Currency currency,
    BigDecimal baseAmount,
    BigDecimal feesTotal,
    Instant sentAt) {}
