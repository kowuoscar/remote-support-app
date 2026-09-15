package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.StandingAmountType;
import com.remotesupport.backend.dto.AgentInvoiceResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.security.AgentInvoiceAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An Agent's own monthly Agent Invoice (spec.md Solution's Agent Invoice entity;
 * agent-standing-amounts-and-invoice-generation ticket, user stories 25-26). One per Agent per
 * calendar month, get-or-created on first access — the exact same shape {@link
 * ClientInvoiceController} established for Client Invoices (see its Javadoc): {@code GET} is
 * idempotent-with-a-side-effect, and a second concurrent first-view racing to create the same
 * month's draft is resolved by the unique {@code (agent_id, billing_month)} constraint (V14
 * migration), not application-level locking.
 *
 * <p><b>Every line item is computed live, straight from the source data — never through a Client
 * Invoice.</b> The Local Support Fees line is "the sum, across every one of the Agent's
 * Contracts, of that Contract's base amount + Fee total for the month" (spec.md), computed via
 * {@link ContractAmountService} directly against {@code sim_cards}/{@code fees} — deliberately
 * never by reading a {@code ClientInvoice} row (its own {@code status}, or its frozen snapshot
 * once {@code sent}/{@code approved}) at all. This is not an oversight: the Agent already fronted
 * every base charge and Fee the moment it happened, regardless of how far that Contract's
 * separate Client-facing paperwork has progressed through its own review — a Fee logged after
 * that Contract's Client Invoice was already sent (and therefore excluded from its frozen
 * snapshot, per {@code ClientInvoice}'s Javadoc) must still count here. Reading the live totals
 * directly is simpler than reconciling two different "was this Fee counted" rules for one number
 * and is exactly what the ticket's regression note protects: Client Invoice amounts are only ever
 * read elsewhere, and are entirely unaffected by anything this controller does.
 *
 * <p>Salary and the two Rollout Advance lines resolve from {@link StandingAmountService} for this
 * invoice's own {@code billingMonth} (salary, new advance) and the month before it (advance
 * repayment) — see that service and {@link com.remotesupport.backend.domain.AgentStandingAmount}
 * for the next-month-effective versioning this relies on.
 */
@RestController
@RequestMapping("/api/agents/{agentId}/invoice")
public class AgentInvoiceController {

  private final AgentRepository agentRepository;
  private final AgentInvoiceRepository agentInvoiceRepository;
  private final ContractRepository contractRepository;
  private final ContractAmountService contractAmountService;
  private final StandingAmountService standingAmountService;
  private final AgentInvoiceAccessGuard agentInvoiceAccessGuard;

  public AgentInvoiceController(
      AgentRepository agentRepository,
      AgentInvoiceRepository agentInvoiceRepository,
      ContractRepository contractRepository,
      ContractAmountService contractAmountService,
      StandingAmountService standingAmountService,
      AgentInvoiceAccessGuard agentInvoiceAccessGuard) {
    this.agentRepository = agentRepository;
    this.agentInvoiceRepository = agentInvoiceRepository;
    this.contractRepository = contractRepository;
    this.contractAmountService = contractAmountService;
    this.standingAmountService = standingAmountService;
    this.agentInvoiceAccessGuard = agentInvoiceAccessGuard;
  }

  @GetMapping
  public AgentInvoiceResponse get(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent = findAgent(agentId, principal);
    agentInvoiceAccessGuard.requireCanBuildOrView(agent, principal);

    AgentInvoice invoice = getOrCreateDraftForCurrentMonth(agent, principal);
    return buildResponse(agent, invoice);
  }

  private AgentInvoiceResponse buildResponse(Agent agent, AgentInvoice invoice) {
    LocalDate month = invoice.getBillingMonth();

    BigDecimal localSupportFees =
        contractRepository.findByTenantIdAndAgentIdOrderByCreatedAtAsc(agent.getTenant().getId(), agent.getId())
            .stream()
            .map(Contract::getId)
            .map(contractId -> contractAmountService.totalForMonth(contractId, month))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal salary = standingAmountService.resolve(agent.getId(), StandingAmountType.SALARY, month);
    BigDecimal previousAdvance =
        standingAmountService.resolve(agent.getId(), StandingAmountType.ROLLOUT_ADVANCE, month.minusMonths(1));
    BigDecimal newAdvance =
        standingAmountService.resolve(agent.getId(), StandingAmountType.ROLLOUT_ADVANCE, month);
    BigDecimal repayment = previousAdvance.negate();

    BigDecimal totalAmount = localSupportFees.add(salary).add(repayment).add(newAdvance);

    return new AgentInvoiceResponse(
        invoice.getId(),
        agent.getId(),
        month,
        invoice.getStatus().name(),
        invoice.getCurrency().name(),
        localSupportFees,
        salary,
        repayment,
        newAdvance,
        totalAmount);
  }

  private AgentInvoice getOrCreateDraftForCurrentMonth(Agent agent, AuthenticatedPrincipal principal) {
    LocalDate billingMonth = currentBillingMonth();
    return agentInvoiceRepository
        .findByAgentIdAndBillingMonth(agent.getId(), billingMonth)
        .orElseGet(() -> createDraft(agent, billingMonth, principal));
  }

  private AgentInvoice createDraft(Agent agent, LocalDate billingMonth, AuthenticatedPrincipal principal) {
    AgentInvoice invoice = new AgentInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(agent.getTenant());
    invoice.setAgent(agent);
    invoice.setBillingMonth(billingMonth);
    invoice.setStatus(AgentInvoiceStatus.DRAFT);
    invoice.setCurrency(agent.getCurrency());
    invoice.setCreatedAt(Instant.now());

    try {
      agentInvoiceRepository.saveAndFlush(invoice);
    } catch (DataIntegrityViolationException raceLost) {
      // Another request racing to view this same Agent/month for the first time already won
      // (V14's unique (agent_id, billing_month) constraint) — fall back to the row it created,
      // mirroring ClientInvoiceController#createDraft's identical race-handling note.
      return agentInvoiceRepository
          .findByAgentIdAndBillingMonth(agent.getId(), billingMonth)
          .orElseThrow(() -> raceLost);
    }

    AuditLog.created("AgentInvoice", invoice.getId(), principal.userId(), principal.tenantId());
    return invoice;
  }

  private Agent findAgent(UUID agentId, AuthenticatedPrincipal principal) {
    return agentRepository
        .findByIdAndTenantId(agentId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No agent with id " + agentId));
  }

  private LocalDate currentBillingMonth() {
    return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
  }
}
