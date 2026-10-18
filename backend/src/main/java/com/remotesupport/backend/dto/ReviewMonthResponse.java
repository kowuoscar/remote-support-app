package com.remotesupport.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An Agent's month as the Manager reviews it: the Client Invoices that reached the Manager, in
 * working order, and the Agent Invoice once it is past {@code DRAFT} (else {@code null}).
 * {@code reviewStatus} is {@code TO_REVIEW}, {@code APPROVED} or {@code SENT_BACK}.
 */
public record ReviewMonthResponse(
    UUID agentId,
    String agentName,
    LocalDate billingMonth,
    Instant monthSentAt,
    List<ClientInvoiceItem> clientInvoices,
    AgentInvoiceItem agentInvoice) {

  public static final String TO_REVIEW = "TO_REVIEW";
  public static final String APPROVED = "APPROVED";
  public static final String SENT_BACK = "SENT_BACK";

  public record ClientInvoiceItem(
      UUID id,
      UUID contractId,
      String clientName,
      String reviewStatus,
      String currency,
      BigDecimal totalAmount,
      Instant sentAt,
      Instant approvedAt,
      Instant sentBackAt) {}

  public record AgentInvoiceItem(UUID id, String status, String currency, BigDecimal totalAmount) {}
}
