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
 *
 * <p>{@code description} (request-types-and-flow spec, Details at submission;
 * other-replaces-repair ticket): optional for every type except {@code OTHER}, where {@link
 * com.remotesupport.backend.web.RequestController} refuses a blank one — the same rule the
 * Agent-proactive path in this same controller and the proactive-Fee path in {@link
 * com.remotesupport.backend.web.FeeController} both enforce.
 *
 * <p>{@code targetSmartphoneId}/{@code targetSimCardId}/{@code topupOptionId}
 * (reboot-and-topup-details ticket): a Reboot Request's target Smartphone, and a Topup Request's
 * target SIM Card and, when its Carrier has one, the Topup Option it asks for — validated and
 * stored by {@link com.remotesupport.backend.web.requestdetails.RequestDetailsValidator}, the one
 * module both this controller and {@link com.remotesupport.backend.web.FeeController}'s
 * proactive-Fee path call. Meaningless, and ignored, for every other type.
 */
public record RequestCreateRequest(
    @NotNull RequestType type,
    UUID testerId,
    RequestStatus startingStatus,
    String description,
    @Valid SmartphoneCreateRequest newSmartphone,
    @Valid SimCardCreateRequest newSimCard,
    UUID replacesSmartphoneId,
    UUID replacesSimCardId,
    UUID targetSmartphoneId,
    UUID targetSimCardId,
    UUID topupOptionId) {}
