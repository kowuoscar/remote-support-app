package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.dto.ReviewQueueItemResponse;
import com.remotesupport.backend.repository.AgentInvoiceQueueRow;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.ClientInvoiceQueueRow;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Manager's Review Queue (CONTEXT.md; manager-invoice-review-queue spec): every invoice in the
 * caller's tenant waiting on a Manager action — each Client Invoice in {@code SENT} and each Agent
 * Invoice in {@code SENT} or {@code APPROVED} — from any Contract, Agent and billing month, longest
 * waiting first with the invoice id as a stable tiebreak. Manager-only at the matcher level
 * (SecurityConfig). Read-only: it never creates or changes an invoice.
 */
@RestController
public class ReviewQueueController {

  private static final Logger log = LoggerFactory.getLogger(ReviewQueueController.class);

  private static final Comparator<ReviewQueueItemResponse> LONGEST_WAITING_FIRST =
      Comparator.comparing(ReviewQueueItemResponse::waitingSince)
          .thenComparing(item -> item.id().toString());

  private static final List<AgentInvoiceStatus> AGENT_INVOICE_WAITING_STATUSES =
      List.of(AgentInvoiceStatus.SENT, AgentInvoiceStatus.APPROVED);

  private final ClientInvoiceRepository clientInvoiceRepository;
  private final AgentInvoiceRepository agentInvoiceRepository;

  public ReviewQueueController(
      ClientInvoiceRepository clientInvoiceRepository, AgentInvoiceRepository agentInvoiceRepository) {
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.agentInvoiceRepository = agentInvoiceRepository;
  }

  @GetMapping("/api/review-queue")
  public List<ReviewQueueItemResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Stream<ReviewQueueItemResponse> clientInvoices =
        clientInvoiceRepository.findQueueRows(principal.tenantId(), ClientInvoiceStatus.SENT).stream()
            .map(ReviewQueueController::fromClientInvoice);
    Stream<ReviewQueueItemResponse> agentInvoices =
        agentInvoiceRepository.findQueueRows(principal.tenantId(), AGENT_INVOICE_WAITING_STATUSES).stream()
            .map(ReviewQueueController::fromAgentInvoice);
    List<ReviewQueueItemResponse> items =
        Stream.concat(clientInvoices, agentInvoices).sorted(LONGEST_WAITING_FIRST).toList();
    log.debug("review queue read tenantId={} items={}", principal.tenantId(), items.size());
    return items;
  }

  private static ReviewQueueItemResponse fromClientInvoice(ClientInvoiceQueueRow row) {
    BigDecimal feesTotal = row.snapshotFeesTotal() != null ? row.snapshotFeesTotal() : BigDecimal.ZERO;
    return new ReviewQueueItemResponse(
        ReviewQueueItemResponse.CLIENT_INVOICE,
        row.id(),
        row.status().name(),
        row.billingMonth(),
        row.contractId(),
        row.clientName(),
        null,
        row.agentName(),
        row.currency().name(),
        row.snapshotBaseAmount().add(feesTotal),
        row.sentAt());
  }

  /** An approved Agent Invoice is waiting to be marked paid, so it has waited since its approval. */
  private static ReviewQueueItemResponse fromAgentInvoice(AgentInvoiceQueueRow row) {
    return new ReviewQueueItemResponse(
        ReviewQueueItemResponse.AGENT_INVOICE,
        row.id(),
        row.status().name(),
        row.billingMonth(),
        null,
        null,
        row.agentId(),
        row.agentName(),
        row.currency().name(),
        row.snapshotTotal(),
        row.status() == AgentInvoiceStatus.APPROVED ? row.approvedAt() : row.sentAt());
  }
}
