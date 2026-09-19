package com.remotesupport.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A new Topup Option or Postpaid Plan, or an edit of one: both fields are always sent. The price
 * is a positive amount in the Carrier's Country's currency; no currency is ever sent.
 */
public record CarrierOfferRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 10, fraction = 2)
        BigDecimal price) {}
