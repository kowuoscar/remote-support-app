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
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * What every Agent Invoice route does once it has found its invoice, whether it was addressed as
 * "this Agent's invoice for the current month" ({@link AgentInvoiceController}) or by its own id
 * ({@link AgentInvoiceByIdController}): the response shape with its live-while-draft,
 * frozen-from-sent reads (ADR 0003), the send snapshot, the Manager's per-invoice override and the
 * approve and mark-paid transitions, each with its audit event. The two controllers differ only in
 * how they find the invoice and who may call them — the same split {@link ClientInvoiceService}
 * made for Client Invoices.
 */
@Component
public class AgentInvoiceService {

  private final AgentInvoiceRepository agentInvoiceRepository;
  private final ContractRepository contractRepository;
  private final ContractAmountService contractAmountService;
  private final StandingAmountService standingAmountService;

  public AgentInvoiceService(
      AgentInvoiceRepository agentInvoiceRepository,
      ContractRepository contractRepository,
      ContractAmountService contractAmountService,
      StandingAmountService standingAmountService) {
    this.agentInvoiceRepository = agentInvoiceRepository;
    this.contractRepository = contractRepository;
    this.contractAmountService = contractAmountService;
    this.standingAmountService = standingAmountService;
  }

  /**
   * Sends a draft ({@code DRAFT -> SENT}), snapshotting every line in the same transaction as the
   * status change so the two can never disagree; any other status is a 409.
   */
  public AgentInvoiceResponse send(AgentInvoice invoice, AuthenticatedPrincipal principal) {
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
  public AgentInvoiceResponse override(
      AgentInvoice invoice, AgentInvoiceOverrideRequest request, AuthenticatedPrincipal principal) {
    if (request.salary() == null && request.rolloutAdvanceNewAdvance() == null) {
      throw new InvalidRequestException(
          "Provide at least one of salary or rolloutAdvanceNewAdvance to override");
    }
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
  public AgentInvoiceResponse approve(AgentInvoice invoice, AuthenticatedPrincipal principal) {
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
  public AgentInvoiceResponse markPaid(AgentInvoice invoice, AuthenticatedPrincipal principal) {
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
   * Local Support Fees: every one of the Agent's Contracts' base amount + Fee total for the month,
   * computed straight from the source data — never through a Client Invoice (ADR 0002).
   */
  private BigDecimal computeLocalSupportFees(Agent agent, LocalDate month) {
    return contractRepository
        .findByTenantIdAndAgentIdOrderByCreatedAtAsc(agent.getTenant().getId(), agent.getId())
        .stream()
        .map(Contract::getId)
        .map(contractId -> contractAmountService.totalForMonth(contractId, month))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
