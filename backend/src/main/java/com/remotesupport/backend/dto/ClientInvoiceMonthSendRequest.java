package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * The optional body of the month send: the invoices the Agent saw. When present and different from
 * the invoices the Agent's month holds, the send is refused ({@code MONTH_CHANGED}); absent (no body,
 * or no {@code invoiceIds}), the batch is whatever the month holds.
 */
public record ClientInvoiceMonthSendRequest(List<@NotNull UUID> invoiceIds) {}
