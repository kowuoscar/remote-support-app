package com.remotesupport.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One Client Invoice sent back to an Agent, as their sent-back list shows it: which invoice and
 * why, and deliberately no amounts (send-a-client-invoice-back spec, "The sent-back list").
 */
public record ClientInvoiceSentBackResponse(
    UUID id,
    UUID contractId,
    String clientName,
    String country,
    LocalDate billingMonth,
    String currency,
    Instant sentBackAt,
    String sentBackReason) {}
