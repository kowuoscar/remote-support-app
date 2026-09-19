package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.SimCard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One Postpaid SIM Card counted in a draft Client Invoice's live base amount (spec.md Solution,
 * "Billing a cancelled Postpaid SIM"; cancelled-sim-billed-through-its-month ticket AC: "The draft
 * Client Invoice view lists a cancelled SIM Card it still bills, marked with its cancellation
 * date"). {@code cancellationEffectiveDate} is present only for a SIM Card still billing because
 * it was cancelled on or after this month's first day — absent for every currently-Active one,
 * the same "present only when it applies" shape {@code SimCardResponse} already uses for this
 * field. Live only: a {@code SENT}/{@code APPROVED} invoice has no per-unit breakdown of its
 * frozen {@code snapshotBaseAmount} (ADR 0001 pins only the total, not which SIMs made it up), so
 * this list is populated only while the invoice is {@code DRAFT}.
 */
public record ClientInvoiceBaseSimLineResponse(
    UUID simCardId, String number, BigDecimal monthlyFeeAmount, LocalDate cancellationEffectiveDate) {

  public static ClientInvoiceBaseSimLineResponse of(SimCard simCard) {
    return new ClientInvoiceBaseSimLineResponse(
        simCard.getId(), simCard.getNumber(), simCard.getMonthlyFeeAmount(), simCard.getCancellationEffectiveDate());
  }
}
