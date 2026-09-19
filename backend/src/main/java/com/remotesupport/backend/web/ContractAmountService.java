package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Fee;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardFlavor;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.repository.SimCardRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A Contract's base amount and Fee total for a calendar month — the same math {@code
 * ClientInvoiceController} has computed live since client-invoice-generation, extracted so
 * agent-standing-amounts-and-invoice-generation's Local Support Fees line (the sum, across every
 * one of an Agent's Contracts, of each Contract's base amount + Fee total for the month) can reuse
 * it rather than re-deriving the same math a second time. Deliberately independent of {@link
 * com.remotesupport.backend.domain.ClientInvoice} entirely — it reads straight from {@link
 * SimCardRepository}/{@link FeeRepository}, never from a Client Invoice row or its frozen snapshot
 * — see {@code AgentInvoiceController}'s Javadoc for why: an Agent Invoice's Local Support Fees
 * line must reflect what the Agent has actually fronted regardless of whether/how far that
 * Contract's own Client Invoice has progressed through its own {@code sent}/{@code approved}
 * review, so a Fee logged after the Client Invoice was already sent (and therefore excluded from
 * that frozen snapshot) still counts here.
 */
@Service
public class ContractAmountService {

  private final SimCardRepository simCardRepository;
  private final FeeRepository feeRepository;

  public ContractAmountService(SimCardRepository simCardRepository, FeeRepository feeRepository) {
    this.simCardRepository = simCardRepository;
    this.feeRepository = feeRepository;
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
}
