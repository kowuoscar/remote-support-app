package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.StandingAmountType;
import com.remotesupport.backend.dto.AgentInvoiceOverrideRequest;
import com.remotesupport.backend.dto.AgentInvoiceResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.security.AgentInvoiceAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.math.BigDecimal;
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
 * SENT} onward every read serves those columns — see {@link #buildResponse} — so neither a Fee
 * logged afterwards nor a mid-cycle standing-amount change can silently move a total the Manager
 * is reviewing or has already approved.
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

  /**
   * Sends this Agent's current-month draft Agent Invoice (ticket AC: "Agent can send their draft
   * Agent Invoice, moving it to status sent; it is no longer editable by the Agent"), snapshotting
   * every line in the same transaction as the status change so the two can never disagree.
   */
  @PostMapping("/send")
  public AgentInvoiceResponse send(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent = findAgent(agentId, principal);
    agentInvoiceAccessGuard.requireCanSend(agent, principal);

    // get-or-create, exactly like GET (class Javadoc): an Agent who never happened to open the
    // draft view first can still send directly, with no separate "build the draft" step.
    AgentInvoice invoice = getOrCreateDraftForCurrentMonth(agent, principal);

    AgentInvoiceStatus oldStatus = invoice.getStatus();
    if (!oldStatus.canTransitionTo(AgentInvoiceStatus.SENT)) {
      throw new ConflictException("Cannot send an Agent Invoice from status " + oldStatus);
    }

    snapshot(agent, invoice);
    invoice.setStatus(AgentInvoiceStatus.SENT);
    invoice.setSentAt(Instant.now());
    agentInvoiceRepository.save(invoice);

    AuditLog.statusChanged(
        "AgentInvoice",
        invoice.getId(),
        oldStatus.name(),
        AgentInvoiceStatus.SENT.name(),
        principal.userId(),
        principal.tenantId());

    return buildResponse(agent, invoice);
  }

  /**
   * A Manager overriding one line of this Agent's current-month sent Agent Invoice (ticket AC:
   * "Manager can override the Salary or Rollout Advance value on that one invoice at approval
   * time, without changing the Agent's standing amount used by future invoices"). Only while
   * {@code SENT} — an approved/paid invoice is the locked final record (same reasoning as {@link
   * com.remotesupport.backend.domain.ClientInvoice} once {@code APPROVED}), and a draft has
   * nothing frozen yet to override.
   *
   * <p><b>Edits this invoice's snapshot columns directly — never {@link
   * com.remotesupport.backend.domain.AgentStandingAmount}.</b> This is the crux of the ticket AC:
   * {@link StandingAmountService} is never called here, so the Agent's standing salary/Rollout
   * Advance — and therefore every other invoice, past or future — is completely unaffected. Only
   * Salary and the <b>new advance</b> line are overridable; the repayment line never is (see
   * {@link AgentInvoiceOverrideRequest}'s Javadoc for why).
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

    AgentInvoice invoice = findCurrentMonthInvoice(agent);
    if (invoice.getStatus() != AgentInvoiceStatus.SENT) {
      throw new ConflictException(
          "Can only override an Agent Invoice while it is sent, not " + invoice.getStatus());
    }

    if (request.salary() != null) {
      BigDecimal oldValue = invoice.getSnapshotSalary();
      invoice.setSnapshotSalary(request.salary());
      AuditLog.agentInvoiceOverridden(
          invoice.getId(), "salary", oldValue, request.salary(), principal.userId(), principal.tenantId());
    }
    if (request.rolloutAdvanceNewAdvance() != null) {
      BigDecimal oldValue = invoice.getSnapshotRolloutAdvanceNewAdvance();
      invoice.setSnapshotRolloutAdvanceNewAdvance(request.rolloutAdvanceNewAdvance());
      AuditLog.agentInvoiceOverridden(
          invoice.getId(),
          "rolloutAdvanceNewAdvance",
          oldValue,
          request.rolloutAdvanceNewAdvance(),
          principal.userId(),
          principal.tenantId());
    }
    agentInvoiceRepository.save(invoice);

    return buildResponse(agent, invoice);
  }

  /**
   * Approves this Agent's current-month sent Agent Invoice (ticket AC: "Manager can approve a
   * sent Agent Invoice, moving it to status approved"). Rejects a {@code draft} with a clean
   * {@link ConflictException} (409) — the same invalid-transition pattern {@link
   * ClientInvoiceController#approve} already established, not a silent no-op or a 500.
   */
  @PostMapping("/approve")
  public AgentInvoiceResponse approve(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent = findAgent(agentId, principal);
    agentInvoiceAccessGuard.requireCanApprove(principal);

    AgentInvoice invoice = findCurrentMonthInvoice(agent);
    AgentInvoiceStatus oldStatus = invoice.getStatus();
    if (!oldStatus.canTransitionTo(AgentInvoiceStatus.APPROVED)) {
      throw new ConflictException("Cannot approve an Agent Invoice from status " + oldStatus);
    }

    invoice.setStatus(AgentInvoiceStatus.APPROVED);
    invoice.setApprovedAt(Instant.now());
    agentInvoiceRepository.save(invoice);

    AuditLog.statusChanged(
        "AgentInvoice",
        invoice.getId(),
        oldStatus.name(),
        AgentInvoiceStatus.APPROVED.name(),
        principal.userId(),
        principal.tenantId());

    return buildResponse(agent, invoice);
  }

  /**
   * Marks this Agent's current-month approved Agent Invoice as paid (ticket AC: "Manager can mark
   * an approved Agent Invoice as paid, moving it to status paid; no payment is executed by the
   * app"). Purely a status flag the Manager sets once payment has happened outside the app — no
   * payment-processor integration anywhere in this codebase (spec.md Non-goals).
   */
  @PostMapping("/paid")
  public AgentInvoiceResponse markPaid(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent = findAgent(agentId, principal);
    agentInvoiceAccessGuard.requireCanMarkPaid(principal);

    AgentInvoice invoice = findCurrentMonthInvoice(agent);
    AgentInvoiceStatus oldStatus = invoice.getStatus();
    if (!oldStatus.canTransitionTo(AgentInvoiceStatus.PAID)) {
      throw new ConflictException("Cannot mark an Agent Invoice paid from status " + oldStatus);
    }

    invoice.setStatus(AgentInvoiceStatus.PAID);
    invoice.setPaidAt(Instant.now());
    agentInvoiceRepository.save(invoice);

    AuditLog.statusChanged(
        "AgentInvoice",
        invoice.getId(),
        oldStatus.name(),
        AgentInvoiceStatus.PAID.name(),
        principal.userId(),
        principal.tenantId());

    return buildResponse(agent, invoice);
  }

  /**
   * Freezes this send's four line items (see the class Javadoc for why). Called once, from {@link
   * #send}, inside the same request/transaction as the status change.
   */
  private void snapshot(Agent agent, AgentInvoice invoice) {
    LocalDate month = invoice.getBillingMonth();

    invoice.setSnapshotLocalSupportFees(computeLocalSupportFees(agent, month));
    invoice.setSnapshotSalary(standingAmountService.resolve(agent.getId(), StandingAmountType.SALARY, month));
    BigDecimal previousAdvance =
        standingAmountService.resolve(agent.getId(), StandingAmountType.ROLLOUT_ADVANCE, month.minusMonths(1));
    invoice.setSnapshotRolloutAdvanceRepayment(previousAdvance.negate());
    invoice.setSnapshotRolloutAdvanceNewAdvance(
        standingAmountService.resolve(agent.getId(), StandingAmountType.ROLLOUT_ADVANCE, month));
  }

  private BigDecimal computeLocalSupportFees(Agent agent, LocalDate month) {
    return contractRepository
        .findByTenantIdAndAgentIdOrderByCreatedAtAsc(agent.getTenant().getId(), agent.getId())
        .stream()
        .map(Contract::getId)
        .map(contractId -> contractAmountService.totalForMonth(contractId, month))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private AgentInvoiceResponse buildResponse(Agent agent, AgentInvoice invoice) {
    boolean frozen = invoice.getStatus() != AgentInvoiceStatus.DRAFT;

    BigDecimal localSupportFees;
    BigDecimal salary;
    BigDecimal repayment;
    BigDecimal newAdvance;
    if (frozen) {
      localSupportFees = invoice.getSnapshotLocalSupportFees();
      salary = invoice.getSnapshotSalary();
      repayment = invoice.getSnapshotRolloutAdvanceRepayment();
      newAdvance = invoice.getSnapshotRolloutAdvanceNewAdvance();
    } else {
      LocalDate month = invoice.getBillingMonth();
      localSupportFees = computeLocalSupportFees(agent, month);
      salary = standingAmountService.resolve(agent.getId(), StandingAmountType.SALARY, month);
      BigDecimal previousAdvance =
          standingAmountService.resolve(agent.getId(), StandingAmountType.ROLLOUT_ADVANCE, month.minusMonths(1));
      repayment = previousAdvance.negate();
      newAdvance = standingAmountService.resolve(agent.getId(), StandingAmountType.ROLLOUT_ADVANCE, month);
    }

    BigDecimal totalAmount = localSupportFees.add(salary).add(repayment).add(newAdvance);

    return new AgentInvoiceResponse(
        invoice.getId(),
        agent.getId(),
        invoice.getBillingMonth(),
        invoice.getStatus().name(),
        invoice.getCurrency().name(),
        localSupportFees,
        salary,
        repayment,
        newAdvance,
        totalAmount,
        invoice.getSentAt(),
        invoice.getApprovedAt(),
        invoice.getPaidAt());
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
