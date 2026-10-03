package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.StandingAmountType;
import com.remotesupport.backend.dto.AgentInvoiceOverrideRequest;
import com.remotesupport.backend.dto.AgentInvoiceResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What every Agent Invoice route does once it has found its invoice, whether it was addressed as
 * "this Agent's invoice for the current month" ({@link AgentInvoiceController}, which today is
 * only the Agent's own view/build and send) or by its own id ({@link AgentInvoiceByIdController},
 * which carries every Manager action): the response shape with its live-while-draft,
 * frozen-from-sent reads (ADR 0003), the send snapshot, the Manager's per-invoice override and the
 * approve and mark-paid transitions, each with its audit event. The two controllers differ only in
 * how they find the invoice and who may call them — the same split {@link ClientInvoiceService}
 * made for Client Invoices.
 */
@Component
public class AgentInvoiceService {

  private final AgentInvoiceRepository agentInvoiceRepository;
  private final AgentRepository agentRepository;
  private final ContractRepository contractRepository;
  private final ContractAmountService contractAmountService;
  private final StandingAmountService standingAmountService;

  public AgentInvoiceService(
      AgentInvoiceRepository agentInvoiceRepository,
      AgentRepository agentRepository,
      ContractRepository contractRepository,
      ContractAmountService contractAmountService,
      StandingAmountService standingAmountService) {
    this.agentInvoiceRepository = agentInvoiceRepository;
    this.agentRepository = agentRepository;
    this.contractRepository = contractRepository;
    this.contractAmountService = contractAmountService;
    this.standingAmountService = standingAmountService;
  }

  /**
   * Sends a draft ({@code DRAFT -> SENT}), snapshotting every line in the same transaction as the
   * status change so the two can never disagree; any other status is a 409.
   */
  @Transactional
  public AgentInvoiceResponse send(AgentInvoice found, AuthenticatedPrincipal principal) {
    // The Agent's row first, like an edit that found no Agent Invoice: see followClientInvoiceEdit.
    agentRepository.findByIdForUpdate(found.getAgent().getId());
    AgentInvoice invoice = lock(found);
    AgentInvoiceStatus oldStatus = invoice.getStatus();
    if (!oldStatus.canTransitionTo(AgentInvoiceStatus.SENT)) {
      throw new ConflictException("Cannot send an Agent Invoice from status " + oldStatus);
    }

    snapshot(invoice);
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

    return toResponse(invoice);
  }

  /**
   * A Manager overriding Salary and/or the new-advance line of one sent invoice. Only while {@code
   * SENT} — an approved/paid invoice is the locked final record, and a draft has nothing frozen yet
   * to override (409 either way).
   *
   * <p><b>Edits this invoice's snapshot columns directly — never {@link
   * com.remotesupport.backend.domain.AgentStandingAmount}.</b> {@link StandingAmountService} is
   * never called here, so the Agent's standing salary/Rollout Advance — and therefore every other
   * invoice, past or future — is unaffected (ADR 0003). The repayment line is never overridable
   * (see {@link AgentInvoiceOverrideRequest}).
   */
  @Transactional
  public AgentInvoiceResponse override(
      AgentInvoice found, AgentInvoiceOverrideRequest request, AuthenticatedPrincipal principal) {
    if (request.salary() == null && request.rolloutAdvanceNewAdvance() == null) {
      throw new InvalidRequestException(
          "Provide at least one of salary or rolloutAdvanceNewAdvance to override");
    }
    AgentInvoice invoice = lock(found);
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

    return toResponse(invoice);
  }

  /** {@code SENT -> APPROVED}; any other status is a clean 409, never a silent no-op. */
  @Transactional
  public AgentInvoiceResponse approve(AgentInvoice found, AuthenticatedPrincipal principal) {
    AgentInvoice invoice = lock(found);
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

    return toResponse(invoice);
  }

  /**
   * {@code APPROVED -> PAID}; any other status is a 409. Purely a status flag the Manager sets once
   * payment has happened outside the app — no payment is executed here.
   */
  @Transactional
  public AgentInvoiceResponse markPaid(AgentInvoice found, AuthenticatedPrincipal principal) {
    AgentInvoice invoice = lock(found);
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

    return toResponse(invoice);
  }

  /**
   * The invoice's response: while {@code DRAFT}, every line is computed live for the invoice's own
   * billing month; from {@code SENT} onward all four come from the snapshot taken at send.
   */
  @Transactional(readOnly = true)
  public AgentInvoiceResponse toResponse(AgentInvoice invoice) {
    Agent agent = invoice.getAgent();
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
      repayment = previousAdvance(agent, month).negate();
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

  /**
   * A Client Invoice line of this Agent's month was edited (edit-client-invoice-lines spec, "Backend:
   * editing a line", step 7; ADR 0004): locks the Contract's Agent's Agent Invoice for the Client
   * Invoice's billing month, always, whatever its status, and holds the lock to the caller's commit;
   * the Agent's row is locked first, so a first-ever send that creates the invoice cannot slip past.
   * Only a {@code SENT} one moves, by exactly {@code newBilled - oldBilled}, with an audit line: a
   * {@code DRAFT} computes live, an {@code APPROVED} or {@code PAID} one is final (the difference is
   * a carry-over), and no Agent Invoice yet computes live when created. The caller already holds the
   * Client Invoice's lock, so the order is always Client Invoice then Agent Invoice.
   */
  @Transactional
  public void followClientInvoiceEdit(
      ClientInvoice clientInvoice, BigDecimal oldBilled, BigDecimal newBilled, AuthenticatedPrincipal principal) {
    UUID agentId = clientInvoice.getContract().getAgent().getId();
    // With no Agent Invoice yet there is no row to lock, and a first-ever send could freeze the
    // fees without this edit. Both take the Agent's row first (send does too), so one waits for
    // the other; lock order stays Client Invoice, Agent, Agent Invoice.
    agentRepository.findByIdForUpdate(agentId);
    agentInvoiceRepository
        .findByAgentIdAndBillingMonthForUpdate(agentId, clientInvoice.getBillingMonth())
        .filter(invoice -> invoice.getStatus() == AgentInvoiceStatus.SENT)
        .ifPresent(
            invoice -> {
              BigDecimal oldFees = invoice.getSnapshotLocalSupportFees();
              BigDecimal newFees = oldFees.add(newBilled.subtract(oldBilled));
              invoice.setSnapshotLocalSupportFees(newFees);
              agentInvoiceRepository.save(invoice);
              AuditLog.agentInvoiceLocalSupportFeesFollowed(
                  invoice.getId(),
                  clientInvoice.getId(),
                  oldFees,
                  newFees,
                  principal.userId(),
                  principal.tenantId());
            });
  }

  /** The invoice re-read under its row lock, so what a transition writes is never from a stale read. */
  private AgentInvoice lock(AgentInvoice found) {
    return agentInvoiceRepository.findByIdForUpdate(found.getId()).orElseThrow();
  }

  /** Freezes the four line items; called once, from {@link #send}. */
  private void snapshot(AgentInvoice invoice) {
    Agent agent = invoice.getAgent();
    LocalDate month = invoice.getBillingMonth();

    invoice.setSnapshotLocalSupportFees(computeLocalSupportFees(agent, month));
    invoice.setSnapshotSalary(standingAmountService.resolve(agent.getId(), StandingAmountType.SALARY, month));
    invoice.setSnapshotRolloutAdvanceRepayment(previousAdvance(agent, month).negate());
    invoice.setSnapshotRolloutAdvanceNewAdvance(
        standingAmountService.resolve(agent.getId(), StandingAmountType.ROLLOUT_ADVANCE, month));
  }

  private BigDecimal previousAdvance(Agent agent, LocalDate month) {
    return standingAmountService.resolve(agent.getId(), StandingAmountType.ROLLOUT_ADVANCE, month.minusMonths(1));
  }

  /**
   * Local Support Fees: the sum, across the Agent's Contracts, of each Contract's payable amount for
   * the month (ADR 0004): its Client Invoice's billed lines plus anything of the month that invoice
   * does not bill, at its computed amount.
   */
  private BigDecimal computeLocalSupportFees(Agent agent, LocalDate month) {
    return contractRepository
        .findByTenantIdAndAgentIdOrderByCreatedAtAsc(agent.getTenant().getId(), agent.getId())
        .stream()
        .map(Contract::getId)
        .map(contractId -> contractAmountService.payableAmountForMonth(contractId, month))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
