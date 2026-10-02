package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.remotesupport.backend.domain.SimCard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One Postpaid SIM Card line of a Client Invoice's base amount (spec.md Solution, "Billing a
 * cancelled Postpaid SIM"; edit-client-invoice-lines spec, "Backend: reading lines").
 * {@code monthlyFeeAmount} is the SIM's own monthly fee; {@code amount} is what the line bills,
 * {@code computedAmount} what the computation said for it, and {@code edited} whether the two
 * differ. A Tester never sees the last two, so both are {@code null} for that caller.
 * {@code cancellationEffectiveDate} is present only for a SIM Card still billing because it was
 * cancelled on or after this month's first day.
 */
public record ClientInvoiceBaseSimLineResponse(
    UUID simCardId,
    String number,
    BigDecimal monthlyFeeAmount,
    LocalDate cancellationEffectiveDate,
    BigDecimal amount,
    @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal computedAmount,
    @JsonInclude(JsonInclude.Include.ALWAYS) Boolean edited) {

  public static ClientInvoiceBaseSimLineResponse of(
      SimCard simCard, BigDecimal amount, BigDecimal computedAmount, Boolean edited) {
    return new ClientInvoiceBaseSimLineResponse(
        simCard.getId(),
        simCard.getNumber(),
        simCard.getMonthlyFeeAmount(),
        simCard.getCancellationEffectiveDate(),
        amount,
        computedAmount,
        edited);
  }
}
