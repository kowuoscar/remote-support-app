package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.RequestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code type} is always required. {@code testerId} and {@code startingStatus} are only
 * meaningful for an Agent-authored creation (agent-request-fulfillment ticket AC: "Agent can log
 * a Request directly ... for one of their own Contracts, starting at Submitted or immediately at
 * Completed") — a Tester-authored creation (tester-request-submission ticket) ignores both: the
 * Tester is inferred from the caller's own login, and status always starts {@code SUBMITTED}.
 *
 * <p>{@code newSmartphone}/{@code newSimCard}/{@code replacesSmartphoneId}/{@code
 * replacesSimCardId} (fee-logging-and-provisioning ticket): only meaningful, and only required,
 * when an Agent logs a {@code PROVISION_SMARTPHONE}/{@code PROVISION_SIM} Request that starts
 * immediately {@code COMPLETED} — the provisioning side-effect that would otherwise run on a
 * later status-transition PATCH ({@link RequestStatusUpdateRequest}) must run right here instead,
 * since the Request never passes through a separate "complete it" step.
 */
public record RequestCreateRequest(
    @NotNull RequestType type,
    UUID testerId,
    RequestStatus startingStatus,
    @Valid SmartphoneCreateRequest newSmartphone,
    @Valid SimCardCreateRequest newSimCard,
    UUID replacesSmartphoneId,
    UUID replacesSimCardId) {}
