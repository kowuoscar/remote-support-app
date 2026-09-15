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
   * spec.md Solution: base amount = "the sum of the monthly fee of every Postpaid SIM active in
   * the Contract's Fleet at the time of viewing" — a Retired Postpaid SIM and every Prepaid SIM
   * (which never carries a monthly fee) are both excluded.
   */
  public BigDecimal baseAmount(UUID contractId) {
    return simCardRepository.findByContractIdOrderByCreatedAtAsc(contractId).stream()
        .filter(sim -> sim.getFlavor() == SimCardFlavor.POSTPAID && sim.getStatus() == SimCardStatus.ACTIVE)
        .map(SimCard::getMonthlyFeeAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
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
    return baseAmount(contractId).add(feesTotal(contractId, billingMonth));
  }
}
