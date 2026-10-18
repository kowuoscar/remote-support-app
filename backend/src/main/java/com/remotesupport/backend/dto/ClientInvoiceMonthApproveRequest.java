package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * The body of a month approval: the invoices the Manager saw left to review. Required and
 * non-empty; it must equal the invoices the Agent's month holds {@code SENT}, or the approval is
 * refused ({@code MONTH_CHANGED}).
 */
public record ClientInvoiceMonthApproveRequest(
    @NotEmpty(message = "invoiceIds must name the invoices to approve") List<@NotNull UUID> invoiceIds) {}
