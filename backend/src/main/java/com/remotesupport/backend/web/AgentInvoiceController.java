package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.dto.AgentInvoiceOverrideRequest;
import com.remotesupport.backend.dto.AgentInvoiceResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.security.AgentInvoiceAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An Agent's own monthly Agent Invoice (spec.md Solution's Agent Invoice entity;
 * agent-standing-amounts-and-invoice-generation ticket, user stories 25-26, completed by
 * agent-invoice-submission-and-approval's send/override/approve/paid transitions, user stories
 * 9-12, 27-28). One per Agent per calendar month, get-or-created on first access — the exact same
 * shape {@link ClientInvoiceController} established for Client Invoices (see its Javadoc): {@code
 * GET} is idempotent-with-a-side-effect, and a second concurrent first-view racing to create the
 * same month's draft is resolved by the unique {@code (agent_id, billing_month)} constraint (V14
 * migration), not application-level locking.
 *
 * <p><b>Live while DRAFT, frozen from SENT onward.</b> While {@code DRAFT}, every line item is
 * computed live, straight from the source data — never through a Client Invoice. The Local
 * Support Fees line is "the sum, across every one of the Agent's Contracts, of that Contract's
 * base amount + Fee total for the month" (spec.md), computed via {@link ContractAmountService}
 * directly against {@code sim_cards}/{@code fees} — deliberately never by reading a {@code
 * ClientInvoice} row (its own {@code status}, or its frozen snapshot once {@code sent}/{@code
 * approved}) at all; see ADR 0002 for the full reasoning. Salary and the two Rollout Advance lines
 * resolve from {@link StandingAmountService} for this invoice's own {@code billingMonth} (salary,
 * new advance) and the month before it (advance repayment).
 *
 * <p>{@link #send} freezes all four lines into the {@code AgentInvoice} row's {@code snapshot*}
 * columns, in the same request as the {@code DRAFT -> SENT} transition (ADR 0001/0003's
 * reasoning, one status further than Client Invoice's single base-amount freeze). From {@code
 * SENT} onward every read serves those columns — see {@link AgentInvoiceService#toResponse} — so neither a Fee
 * logged afterwards nor a mid-cycle standing-amount change can silently move a total the Manager
 * is reviewing or has already approved.
 *
 * <p>What happens once the invoice is found is shared with {@link AgentInvoiceByIdController}
 * through {@link AgentInvoiceService}; this controller only finds "this Agent's invoice for the
 * current month" and checks who may act on it.
 */
@RestController
@RequestMapping("/api/agents/{agentId}/invoice")
public class AgentInvoiceController {

  private final AgentRepository agentRepository;
  private final AgentInvoiceRepository agentInvoiceRepository;
  private final AgentInvoiceService agentInvoiceService;
  private final AgentInvoiceAccessGuard agentInvoiceAccessGuard;

  public AgentInvoiceController(
      AgentRepository agentRepository,
      AgentInvoiceRepository agentInvoiceRepository,
      AgentInvoiceService agentInvoiceService,
      AgentInvoiceAccessGuard agentInvoiceAccessGuard) {
    this.agentRepository = agentRepository;
    this.agentInvoiceRepository = agentInvoiceRepository;
    this.agentInvoiceService = agentInvoiceService;
    this.agentInvoiceAccessGuard = agentInvoiceAccessGuard;
  }

  @GetMapping
  public AgentInvoiceResponse get(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent = findAgent(agentId, principal);
    agentInvoiceAccessGuard.requireCanBuildOrView(agent, principal);

    return agentInvoiceService.toResponse(getOrCreateDraftForCurrentMonth(agent, principal));
  }

  /**
   * Sends this Agent's current-month draft Agent Invoice (ticket AC: "Agent can send their draft
   * Agent Invoice, moving it to status sent; it is no longer editable by the Agent").
   */
  @PostMapping("/send")
  public AgentInvoiceResponse send(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent = findAgent(agentId, principal);
    agentInvoiceAccessGuard.requireCanSend(agent, principal);

    // get-or-create, exactly like GET (class Javadoc): an Agent who never happened to open the
    // draft view first can still send directly, with no separate "build the draft" step.
    return agentInvoiceService.send(getOrCreateDraftForCurrentMonth(agent, principal), principal);
  }

  /**
   * A Manager overriding one line of this Agent's current-month sent Agent Invoice (ticket AC:
   * "Manager can override the Salary or Rollout Advance value on that one invoice at approval
   * time, without changing the Agent's standing amount used by future invoices"). See {@link
   * AgentInvoiceService#override} for the rules.
   */
  @PostMapping("/override")
  public AgentInvoiceResponse override(
      @PathVariable UUID agentId,
      @Valid @RequestBody AgentInvoiceOverrideRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent = findAgent(agentId, principal);
    agentInvoiceAccessGuard.requireCanOverride(principal);

    if (request.salary() == null && request.rolloutAdvanceNewAdvance() == null) {
      throw new InvalidRequestException(
          "Provide at least one of salary or rolloutAdvanceNewAdvance to override");
    }
    return agentInvoiceService.override(findCurrentMonthInvoice(agent), request, principal);
  }

  /** Approves this Agent's current-month sent Agent Invoice; a draft is a 409. */
  @PostMapping("/approve")
  public AgentInvoiceResponse approve(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent = findAgent(agentId, principal);
    agentInvoiceAccessGuard.requireCanApprove(principal);

    return agentInvoiceService.approve(findCurrentMonthInvoice(agent), principal);
  }

  /**
   * Marks this Agent's current-month approved Agent Invoice as paid (ticket AC: "no payment is
   * executed by the app").
   */
  @PostMapping("/paid")
  public AgentInvoiceResponse markPaid(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent = findAgent(agentId, principal);
    agentInvoiceAccessGuard.requireCanMarkPaid(principal);

    return agentInvoiceService.markPaid(findCurrentMonthInvoice(agent), principal);
  }

  private AgentInvoice findCurrentMonthInvoice(Agent agent) {
    return agentInvoiceRepository
        .findByAgentIdAndBillingMonth(agent.getId(), currentBillingMonth())
        .orElseThrow(() -> new NotFoundException("No Agent Invoice for this Agent this month"));
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
