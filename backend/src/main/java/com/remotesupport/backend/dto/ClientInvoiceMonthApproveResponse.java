package com.remotesupport.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What a month approval answers: the month, the one instant its invoices were approved at, and their ids. */
public record ClientInvoiceMonthApproveResponse(LocalDate billingMonth, Instant approvedAt, List<UUID> invoiceIds) {}
