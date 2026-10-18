package com.remotesupport.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What a month send answers: the month, the one instant its invoices were sent at, and their ids. */
public record ClientInvoiceMonthSendResponse(LocalDate billingMonth, Instant sentAt, List<UUID> invoiceIds) {}
