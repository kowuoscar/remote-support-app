package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.SimCardFlavor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code carrierId} names an active Carrier of the Contract's Agent's Country, and {@code
 * postpaidPlanId} an active Postpaid Plan of that Carrier — required exactly when {@code flavor}
 * is {@code POSTPAID}, and refused for a {@code PREPAID} SIM. The monthly fee is copied from the
 * Plan's price and is never submitted. Neither rule is one bean validation can express alone, so
 * {@code SimCardFactory} checks both, the same way on every SIM-creation path.
 */
public record SimCardCreateRequest(
    @NotBlank String number,
    UUID carrierId,
    @NotNull SimCardFlavor flavor,
    UUID postpaidPlanId) {}
