package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.SimCardFlavor;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code carrierId} names an active Carrier of the Contract's Agent's Country, and {@code
 * monthlyFeeAmount} is required exactly when {@code flavor} is {@code POSTPAID}. Neither rule is
 * one bean validation can express alone, so {@code SimCardFactory} checks both, the same way on
 * every SIM-creation path.
 */
public record SimCardCreateRequest(
    @NotBlank String number,
    UUID carrierId,
    @NotNull SimCardFlavor flavor,
    @DecimalMin(value = "0.0", inclusive = true) BigDecimal monthlyFeeAmount) {}
