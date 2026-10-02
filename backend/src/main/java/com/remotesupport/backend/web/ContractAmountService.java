package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Fee;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardFlavor;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceLineKind;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.repository.SimCardRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Contract's amounts for a calendar month. The computation ({@link #totalForMonth}: the base
 * amount of the Postpaid SIMs that bill that month plus the month's Fees) is what a Client Invoice
 * pre-fills from. {@link #payableAmountForMonth} is what the Contract adds to the Agent's Local
 * Support Fees (ADR 0004, amending ADR 0002): the Client Invoice's billed lines plus anything of
 * the month it does not bill, so a Fee the Agent fronted after the invoice was sent still reaches
 * their pay.
 */
@Service
public class ContractAmountService {

  private final SimCardRepository simCardRepository;
  private final FeeRepository feeRepository;

  private final ClientInvoiceRepository clientInvoiceRepository;
  private final ClientInvoiceService clientInvoiceService;

  /**
   * {@code clientInvoiceService} is {@code @Lazy}: it resolves an invoice's lines with this
   * service's computation, and this service resolves a Contract's pay through those same lines, so
   * the bill and the pay can never read a line two ways.
   */
  public ContractAmountService(
      SimCardRepository simCardRepository,
      FeeRepository feeRepository,
      ClientInvoiceRepository clientInvoiceRepository,
      @Lazy ClientInvoiceService clientInvoiceService) {
    this.simCardRepository = simCardRepository;
    this.feeRepository = feeRepository;
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.clientInvoiceService = clientInvoiceService;
  }

  /**
   * spec.md Solution ("Billing a cancelled Postpaid SIM"): base amount for {@code billingMonth} =
   * the sum of the monthly fee of every Postpaid SIM that {@link #billsFor bills} that month —
   * every currently-Active one, plus every one cancelled (returns-and-agent-stock's Return
   * Disposition, CONTEXT.md "Disposition") with an effective date on or after the month's first
   * day. No proration: a SIM either bills the month's full fee or it doesn't
   * (cancelled-sim-billed-through-its-month ticket, Non-goals). A Prepaid SIM never carries a
   * monthly fee and is always excluded.
   */
  public BigDecimal baseAmount(UUID contractId, LocalDate billingMonth) {
    return billablePostpaidSims(contractId, billingMonth).stream()
        .map(SimCard::getMonthlyFeeAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * Every Postpaid SIM of this Contract that bills for {@code billingMonth} — the same set {@link
   * #baseAmount} sums, exposed on its own so a reader (the draft Client Invoice view) can show
   * which SIM Cards make it up, not just the total.
   */
  public List<SimCard> billablePostpaidSims(UUID contractId, LocalDate billingMonth) {
    LocalDate monthStart = billingMonth.withDayOfMonth(1);
    return simCardRepository.findByContractIdOrderByCreatedAtAsc(contractId).stream()
        .filter(sim -> sim.getFlavor() == SimCardFlavor.POSTPAID)
        .filter(sim -> billsFor(sim, monthStart))
        .toList();
  }

  /**
   * A currently-Active SIM always bills. A cancelled one (Status Retired, {@code
   * cancellationEffectiveDate} set — see {@code SimCard}'s Javadoc) bills every month up to and
   * including its cancellation month, and no month after — so it still counts for {@code
   * monthStart} when its effective date falls on or after that same first-of-month day, including
   * a date later than {@code monthStart} (a cancellation whose effect hasn't started yet still
   * bills the months in between). A SIM retired any other way (no cancellation date) never bills.
   */
  private boolean billsFor(SimCard sim, LocalDate monthStart) {
    if (sim.getStatus() == SimCardStatus.ACTIVE) {
      return true;
    }
    LocalDate cancellationDate = sim.getCancellationEffectiveDate();
    return cancellationDate != null && !cancellationDate.isBefore(monthStart);
  }

  /** Every Fee logged against this Contract for {@code billingMonth}, oldest first. */
  public List<Fee> feesForMonth(UUID contractId, LocalDate billingMonth) {
    return feeRepository.findByContractIdAndBillingMonthOrderByCreatedAtAsc(contractId, billingMonth);
  }

  public BigDecimal feesTotal(UUID contractId, LocalDate billingMonth) {
    return feesForMonth(contractId, billingMonth).stream()
        .map(Fee::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** Base amount + Fee total for {@code billingMonth} — a Contract's full reimbursable total. */
  public BigDecimal totalForMonth(UUID contractId, LocalDate billingMonth) {
    return baseAmount(contractId, billingMonth).add(feesTotal(contractId, billingMonth));
  }

  /**
   * What this Contract adds to the Agent's Local Support Fees for {@code billingMonth} (ADR 0004,
   * amending ADR 0002): every line its Client Invoice bills, at its billed amount, plus anything of
   * that month the invoice does not bill, at its computed amount. No Client Invoice: {@link
   * #totalForMonth}. An invoice whose lines are not stored yet bills exactly the computation with
   * the Agent's edits, so it is its billed total. One with stored lines may lack a Fee logged, or a
   * Postpaid SIM added, since it was sent: those count at their computed amount, except that an
   * invoice with a {@code BASE_AMOUNT} line (sent before per-SIM lines) billed its whole base
   * amount, so no SIM is added to it.
   */
  @Transactional(readOnly = true)
  public BigDecimal payableAmountForMonth(UUID contractId, LocalDate billingMonth) {
    ClientInvoice invoice =
        clientInvoiceRepository.findByContractIdAndBillingMonth(contractId, billingMonth).orElse(null);
    if (invoice == null) {
      return totalForMonth(contractId, billingMonth);
    }
    List<ClientInvoiceService.ResolvedLine> lines = clientInvoiceService.lines(invoice);
    BigDecimal payable =
        lines.stream().map(ClientInvoiceService.ResolvedLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (!invoice.isLinesStored()) {
      return payable;
    }

    Set<UUID> billedFees =
        lines.stream()
            .filter(l -> l.kind() == ClientInvoiceLineKind.FEE)
            .map(l -> l.fee().getId())
            .collect(Collectors.toSet());
    for (Fee fee : feesForMonth(contractId, billingMonth)) {
      if (!billedFees.contains(fee.getId())) {
        payable = payable.add(fee.getAmount());
      }
    }
    boolean legacyBase = lines.stream().anyMatch(l -> l.kind() == ClientInvoiceLineKind.BASE_AMOUNT);
    if (!legacyBase) {
      Set<UUID> billedSims =
          lines.stream()
              .filter(l -> l.kind() == ClientInvoiceLineKind.POSTPAID_SIM)
              .map(l -> l.simCard().getId())
              .collect(Collectors.toSet());
      for (SimCard sim : billablePostpaidSims(contractId, billingMonth)) {
        if (!billedSims.contains(sim.getId())) {
          payable = payable.add(sim.getMonthlyFeeAmount());
        }
      }
    }
    return payable;
  }
}
