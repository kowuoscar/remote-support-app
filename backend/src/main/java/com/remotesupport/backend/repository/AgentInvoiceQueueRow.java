package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.domain.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One Agent Invoice as the Review Queue needs it, read in a single query: its Agent's name and its
 * frozen total (the four snapshotted lines summed, including any Manager override). Only
 * meaningful for an invoice that is at least {@code SENT}, since a draft has no snapshot (ADR 0003).
 */
public record AgentInvoiceQueueRow(
    UUID id,
    AgentInvoiceStatus status,
    LocalDate billingMonth,
    UUID agentId,
    String agentName,
    Currency currency,
    BigDecimal snapshotTotal,
    Instant sentAt,
    Instant approvedAt) {}
