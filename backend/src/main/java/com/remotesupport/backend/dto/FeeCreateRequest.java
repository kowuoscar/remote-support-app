package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.FeeType;
import com.remotesupport.backend.domain.SimCardFlavor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code requestId} names the existing Request this Fee traces back to. When it's {@code null},
 * the Fee is proactive (fee-logging-and-provisioning ticket AC: "A Fee the Agent logs with no
 * pre-existing Request auto-creates its linking Request") and {@code testerId} is required — who
 * the auto-created, immediately-{@code COMPLETED} Request is raised on behalf of. {@code feeType}
 * is always required: for an existing Request it must match that Request's type (mapped via
 * {@link FeeType#toRequestType()}); for a proactive Fee it also decides what type of Request gets
 * auto-created. {@code newSmartphone}/{@code newSimCard}/{@code replacesSmartphoneId}/{@code
 * replacesSimCardId} are only meaningful — and only required — when a proactive Fee's {@code
 * feeType} is {@code PROVISION_SMARTPHONE} or {@code PROVISION_SIM}: the auto-created Request
 * starts (and stays) {@code COMPLETED}, so the provisioning side-effect that would otherwise run
 * on a later status-transition PATCH must run immediately, at creation, using these same fields
 * (mirrors {@link RequestStatusUpdateRequest}'s shape for the non-proactive path).
 *
 * <p>{@code topupOptionId} optionally names the Topup Option a Topup Fee was bought from
 * (topup-fee-from-option ticket). It is allowed only when {@code feeType} is {@code TOPUP}, and the
 * Option must be active, of an active Carrier in the Contract's Country. It never sets {@code
 * amount}, which stays required and is whatever the Agent submits.
 *
 * <p>{@code targetSimCardId} (reboot-and-topup-details ticket): only meaningful, and only
 * required, when a proactive Fee's {@code feeType} is {@code TOPUP} — the SIM Card the
 * auto-created linking Request's Topup detail names, validated by the same {@link
 * com.remotesupport.backend.web.requestdetails.RequestDetailsValidator} the Tester and
 * Agent-proactive Request-creation paths call, so logging a Topup Fee with no pre-existing
 * Request is held to the same rule as logging the Request directly. {@code topupOptionId} above
 * doubles as that same Request's own Topup Option once its Fee-side checks pass.
 *
 * <p>{@code requestedModel}/{@code requestedFlavor}/{@code requestedCarrierId}/{@code
 * requestedPostpaidPlanId}/{@code simCardNumber} (provision-request-details ticket): only
 * meaningful, and only required, when a proactive Fee's {@code feeType} is {@code
 * PROVISION_SMARTPHONE}/{@code PROVISION_SIM} — the auto-created linking Request always starts
 * (and stays) {@code COMPLETED}, so it needs both its own submission-time details (ticket AC: "An
 * Agent logging one proactively gives the same details") and, for a Provision SIM, the SIM number
 * needed to complete it immediately (AC: "one that starts at Completed also gives the SIM
 * number") — validated by the same {@link
 * com.remotesupport.backend.web.requestdetails.RequestDetailsValidator} the Request-creation paths
 * call.
 */
public record FeeCreateRequest(
    UUID requestId,
    @NotNull FeeType feeType,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
    String description,
    UUID testerId,
    @Valid SmartphoneCreateRequest newSmartphone,
    @Valid SimCardCreateRequest newSimCard,
    UUID replacesSmartphoneId,
    UUID replacesSimCardId,
    UUID topupOptionId,
    UUID targetSimCardId,
    String requestedModel,
    SimCardFlavor requestedFlavor,
    UUID requestedCarrierId,
    UUID requestedPostpaidPlanId,
    String simCardNumber) {}
