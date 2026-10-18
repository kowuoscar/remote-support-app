package com.remotesupport.backend.repository;

import java.time.LocalDate;

/**
 * One billing month of an Agent's Client Invoices that reached the Manager, and how many of them
 * are still {@code SENT} (to review).
 */
public record ClientInvoiceReviewMonthRow(LocalDate billingMonth, Long toReview) {}
