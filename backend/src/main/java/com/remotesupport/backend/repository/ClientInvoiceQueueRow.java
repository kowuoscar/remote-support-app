package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One Client Invoice as the Review Queue needs it, read in a single query: its Contract's Client
 * and Agent names, and its frozen total's two parts — the snapshotted base amount and the sum of
 * its snapshotted Fee lines ({@code null} when it pinned none). Only meaningful for an invoice
 * that is at least {@code SENT}, since a draft has no snapshot (ADR 0001).
 */
public record ClientInvoiceQueueRow(
    UUID id,
    ClientInvoiceStatus status,
    LocalDate billingMonth,
    UUID contractId,
    String clientName,
    String agentName,
    Currency currency,
    BigDecimal snapshotBaseAmount,
    BigDecimal snapshotFeesTotal,
    Instant sentAt) {}
