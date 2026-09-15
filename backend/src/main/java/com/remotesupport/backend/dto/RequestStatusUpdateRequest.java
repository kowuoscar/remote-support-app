package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.RequestStatus;
import jakarta.validation.constraints.NotNull;

/**
 * {@code cancellationReason} is required only when {@code status} is {@code CANCELLED}
 * (agent-request-fulfillment ticket AC: "Agent can cancel a Request ... with a reason") —
 * enforced in {@code RequestController}, not here, since it's a cross-field rule bean validation
 * can't express alone (mirrors {@code SimCardCreateRequest}'s Postpaid/monthlyFeeAmount check).
 */
public record RequestStatusUpdateRequest(@NotNull RequestStatus status, String cancellationReason) {}
