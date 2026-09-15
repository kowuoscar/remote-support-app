package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.SimCardFlavor;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * {@code monthlyFeeAmount} is required exactly when {@code flavor} is {@code POSTPAID} — a
 * cross-field rule bean validation can't express with annotations alone, so
 * {@code SimCardController} checks it explicitly.
 */
public record SimCardCreateRequest(
    @NotBlank String number,
    String carrier,
    @NotNull SimCardFlavor flavor,
    @DecimalMin(value = "0.0", inclusive = true) BigDecimal monthlyFeeAmount) {}
