package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.remotesupport.backend.domain.Fee;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One Fee line of a Client Invoice: every field {@link FeeResponse} has, except that {@code amount}
 * is the billed amount of the line (what the Agent set, or the Fee's own amount if never edited),
 * plus {@code computedAmount} (the Fee's amount) and {@code edited}. A Tester never sees the last
 * two, so both are {@code null} for that caller and serialised as such. {@link FeeResponse} itself
 * is untouched, so the Fee list and a Request's Fees still show the Fee as logged.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientInvoiceFeeLineResponse(
    UUID id,
    UUID contractId,
    UUID requestId,
    String requestType,
    String feeType,
    BigDecimal amount,
    String currency,
    String description,
    LocalDate billingMonth,
    Instant createdAt,
    UUID topupOptionId,
    String topupOptionName,
    @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal computedAmount,
    @JsonInclude(JsonInclude.Include.ALWAYS) Boolean edited) {

  public static ClientInvoiceFeeLineResponse of(Fee fee, BigDecimal amount, BigDecimal computedAmount, Boolean edited) {
    FeeResponse asLogged = FeeResponse.of(fee);
    return new ClientInvoiceFeeLineResponse(
        asLogged.id(),
        asLogged.contractId(),
        asLogged.requestId(),
        asLogged.requestType(),
        asLogged.feeType(),
        amount,
        asLogged.currency(),
        asLogged.description(),
        asLogged.billingMonth(),
        asLogged.createdAt(),
        asLogged.topupOptionId(),
        asLogged.topupOptionName(),
        computedAmount,
        edited);
  }
}
