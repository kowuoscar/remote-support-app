package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.remotesupport.backend.domain.Fee;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeeResponse(
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
    String topupOptionName) {

  public static FeeResponse of(Fee fee) {
    return new FeeResponse(
        fee.getId(),
        fee.getContract().getId(),
        fee.getRequest().getId(),
        fee.getRequest().getType().name(),
        fee.getFeeType().name(),
        fee.getAmount(),
        fee.getCurrency().name(),
        fee.getDescription(),
        fee.getBillingMonth(),
        fee.getCreatedAt(),
        fee.getTopupOption() == null ? null : fee.getTopupOption().getId(),
        fee.getTopupOption() == null ? null : fee.getTopupOption().getName());
  }
}
