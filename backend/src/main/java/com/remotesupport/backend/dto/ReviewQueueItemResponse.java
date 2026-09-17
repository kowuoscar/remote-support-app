package com.remotesupport.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One invoice waiting on a Manager action (CONTEXT.md "Review Queue"). {@code kind} tells the two
 * invoice types apart; {@code totalAmount} always comes from the invoice's frozen snapshot, since
 * every queue member is at least {@code SENT}; {@code waitingSince} is when the invoice entered
 * the status it is waiting in, and the queue is ordered by it. {@code contractId} and {@code
 * clientName} are set only for a Client Invoice, {@code agentId} only for an Agent Invoice; {@code
 * agentName} is set for both (a Client Invoice's Contract's Agent).
 */
public record ReviewQueueItemResponse(
    String kind,
    UUID id,
    String status,
    LocalDate billingMonth,
    UUID contractId,
    String clientName,
    UUID agentId,
    String agentName,
    String currency,
    BigDecimal totalAmount,
    Instant waitingSince) {

  public static final String CLIENT_INVOICE = "CLIENT_INVOICE";
  public static final String AGENT_INVOICE = "AGENT_INVOICE";
}
