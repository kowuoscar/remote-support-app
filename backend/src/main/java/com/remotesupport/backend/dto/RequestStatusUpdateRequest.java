package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.RequestStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code cancellationReason} is required only when {@code status} is {@code CANCELLED}
 * (agent-request-fulfillment ticket AC: "Agent can cancel a Request ... with a reason") —
 * enforced in {@code RequestController}, not here, since it's a cross-field rule bean validation
 * can't express alone (mirrors {@code SimCardCreateRequest}'s Postpaid/monthlyFeeAmount check).
 *
 * <p>{@code newSmartphone}/{@code newSimCard}/{@code replacesSmartphoneId}/{@code
 * replacesSimCardId} (fee-logging-and-provisioning ticket): required only when {@code status} is
 * {@code COMPLETED} and the Request being completed is a {@code PROVISION_SMARTPHONE}/{@code
 * PROVISION_SIM} — the Agent supplies the new Fleet unit's details (same field shape as the
 * Manager's {@link SmartphoneCreateRequest}/{@link SimCardCreateRequest}) and, optionally, which
 * existing unit it retires.
 */
public record RequestStatusUpdateRequest(
    @NotNull RequestStatus status,
    String cancellationReason,
    @Valid SmartphoneCreateRequest newSmartphone,
    @Valid SimCardCreateRequest newSimCard,
    UUID replacesSmartphoneId,
    UUID replacesSimCardId) {}
