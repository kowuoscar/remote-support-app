package com.remotesupport.backend.repository;

import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.Currency;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One sent-back Client Invoice as the Agent's sent-back list needs it: no lines, no amounts. */
public record ClientInvoiceSentBackRow(
    UUID id,
    UUID contractId,
    String clientName,
    Country country,
    LocalDate billingMonth,
    Currency currency,
    Instant sentBackAt,
    String sentBackReason) {}
