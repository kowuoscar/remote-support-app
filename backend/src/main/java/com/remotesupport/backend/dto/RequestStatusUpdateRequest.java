package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.RequestStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code cancellationReason} is required only when {@code status} is {@code CANCELLED}
 * (agent-request-fulfillment ticket AC: "Agent can cancel a Request ... with a reason") —
 * enforced in {@code RequestController}, not here, since it's a cross-field rule bean validation
 * can't express alone (mirrors {@code SimCardCreateRequest}'s Postpaid/Plan check).
 *
 * <p>{@code newSmartphone}/{@code newSimCard}/{@code replacesSmartphoneId}/{@code
 * replacesSimCardId} (fee-logging-and-provisioning ticket): the legacy full-form completion, still
 * used exactly as before for a Provision Smartphone/SIM Request that predates
 * provision-request-details (no {@code requestedModel}/{@code requestedFlavor} of its own). {@code
 * simCardNumber} (provision-request-details ticket) is the new-style completion's own, much
 * narrower field: a Provision SIM Request that already carries its Carrier/flavor/Plan from
 * submission asks the Agent for only the SIM number here; a Provision Smartphone Request needs no
 * field here at all (ticket AC: "Completing a Provision Smartphone needs no Agent input"). {@link
 * com.remotesupport.backend.web.ProvisioningService} decides which shape applies per Request,
 * never both.
 */
public record RequestStatusUpdateRequest(
    @NotNull RequestStatus status,
    String cancellationReason,
    @Valid SmartphoneCreateRequest newSmartphone,
    @Valid SimCardCreateRequest newSimCard,
    UUID replacesSmartphoneId,
    UUID replacesSimCardId,
    String simCardNumber) {}
