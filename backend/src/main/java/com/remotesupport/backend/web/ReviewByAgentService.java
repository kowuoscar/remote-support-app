package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.dto.ReviewAgentResponse;
import com.remotesupport.backend.dto.ReviewMonthResponse;
import com.remotesupport.backend.dto.ReviewMonthSummaryResponse;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.AgentInvoiceReviewRow;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.AgentWaitingRow;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ClientInvoiceReviewMonthRow;
import com.remotesupport.backend.repository.ClientInvoiceReviewRow;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Manager's review of invoices by Agent (manager-reviews-by-agent spec, "The model"): what
 * waits on the Manager per Agent and per month, derived from the invoices' own status and {@code
 * firstSentAt}. Read-only; every read is tenant-scoped and an unknown or other-tenant Agent is a
 * {@link NotFoundException}.
 */
@Service
@Transactional(readOnly = true)
public class ReviewByAgentService {

  private static final List<AgentInvoiceStatus> AGENT_INVOICE_WAITING_STATUSES =
      List.of(AgentInvoiceStatus.SENT, AgentInvoiceStatus.APPROVED);

  private final AgentRepository agentRepository;
  private final ClientInvoiceRepository clientInvoiceRepository;
  private final AgentInvoiceRepository agentInvoiceRepository;

  public ReviewByAgentService(
      AgentRepository agentRepository,
      ClientInvoiceRepository clientInvoiceRepository,
      AgentInvoiceRepository agentInvoiceRepository) {
    this.agentRepository = agentRepository;
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.agentInvoiceRepository = agentInvoiceRepository;
  }

  /** Every Agent of the tenant by {@code lower(name)} then id, with what waits on the Manager. */
  public List<ReviewAgentResponse> agents(UUID tenantId) {
    Map<UUID, Long> waiting = new HashMap<>();
    for (AgentWaitingRow row : clientInvoiceRepository.countSentByAgent(tenantId)) {
      waiting.merge(row.agentId(), row.count(), Long::sum);
    }
    for (AgentWaitingRow row :
        agentInvoiceRepository.countWaitingByAgent(tenantId, AGENT_INVOICE_WAITING_STATUSES)) {
      waiting.merge(row.agentId(), row.count(), Long::sum);
    }
    return agentRepository.findByTenantId(tenantId).stream()
        .sorted(
            Comparator.comparing((Agent a) -> a.getName().toLowerCase())
                .thenComparing(Agent::getId))
        .map(a -> new ReviewAgentResponse(a.getId(), a.getName(), waiting.getOrDefault(a.getId(), 0L)))
        .toList();
  }

  /** The Agent's months newest first: those with an invoice that reached the Manager or a non-DRAFT Agent Invoice. */
  public List<ReviewMonthSummaryResponse> months(UUID tenantId, UUID agentId) {
    agent(tenantId, agentId);
    Map<LocalDate, Long> waiting = new HashMap<>();
    for (ClientInvoiceReviewMonthRow row : clientInvoiceRepository.findReviewMonthRows(tenantId, agentId)) {
      waiting.merge(row.billingMonth(), row.toReview(), Long::sum);
    }
    for (AgentInvoiceReviewRow row : agentInvoiceRepository.findNonDraftRows(tenantId, agentId)) {
      boolean waits = AGENT_INVOICE_WAITING_STATUSES.contains(row.status());
      waiting.merge(row.billingMonth(), waits ? 1L : 0L, Long::sum);
    }
    return waiting.entrySet().stream()
        .sorted(Map.Entry.<LocalDate, Long>comparingByKey().reversed())
        .map(e -> new ReviewMonthSummaryResponse(e.getKey(), e.getValue()))
        .toList();
  }

  /** The Agent's month as the Manager reviews it. */
  public ReviewMonthResponse month(UUID tenantId, UUID agentId, LocalDate billingMonth) {
    Agent agent = agent(tenantId, agentId);
    List<ReviewMonthResponse.ClientInvoiceItem> invoices =
        clientInvoiceRepository.findReviewRows(tenantId, agentId, billingMonth).stream()
            .map(ReviewByAgentService::item)
            .toList();
    ReviewMonthResponse.AgentInvoiceItem agentInvoice =
        agentInvoiceRepository
            .findNonDraftRow(tenantId, agentId, billingMonth)
            .map(
                r ->
                    new ReviewMonthResponse.AgentInvoiceItem(
                        r.id(), r.status().name(), r.currency().name(), r.totalAmount()))
            .orElse(null);
    return new ReviewMonthResponse(
        agent.getId(),
        agent.getName(),
        billingMonth,
        clientInvoiceRepository.monthSentAt(agentId, billingMonth),
        invoices,
        agentInvoice);
  }

  private Agent agent(UUID tenantId, UUID agentId) {
    return agentRepository
        .findByIdAndTenantId(agentId, tenantId)
        .orElseThrow(() -> new NotFoundException("No Agent with id " + agentId));
  }

  private static ReviewMonthResponse.ClientInvoiceItem item(ClientInvoiceReviewRow row) {
    String reviewStatus =
        switch (row.status()) {
          case SENT -> ReviewMonthResponse.TO_REVIEW;
          case APPROVED -> ReviewMonthResponse.APPROVED;
          case DRAFT -> ReviewMonthResponse.SENT_BACK;
        };
    return new ReviewMonthResponse.ClientInvoiceItem(
        row.id(),
        row.contractId(),
        row.clientName(),
        reviewStatus,
        row.currency().name(),
        row.totalAmount(),
        row.sentAt(),
        row.approvedAt(),
        row.sentBackAt());
  }
}
