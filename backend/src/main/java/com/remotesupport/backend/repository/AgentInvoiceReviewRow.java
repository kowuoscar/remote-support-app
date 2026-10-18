package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.domain.Currency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** An Agent Invoice past {@code DRAFT} as the Manager's month shows it, with its frozen total. */
public record AgentInvoiceReviewRow(
    UUID id, AgentInvoiceStatus status, LocalDate billingMonth, Currency currency, BigDecimal totalAmount) {}
