package com.remotesupport.backend.web.completion;

import com.remotesupport.backend.dto.SimCardCreateRequest;
import com.remotesupport.backend.dto.SmartphoneCreateRequest;
import java.util.UUID;

/**
 * The completion-time fields a Request's status-transition body can carry, gathered from
 * whichever DTO reached {@code COMPLETED} — {@link com.remotesupport.backend.dto.RequestStatusUpdateRequest}
 * (a later PATCH), {@link com.remotesupport.backend.dto.RequestCreateRequest} (an Agent-proactive
 * Request starting immediately Completed) or {@link com.remotesupport.backend.dto.FeeCreateRequest}
 * (a proactive Fee's auto-created linking Request, always immediately Completed) — into one shape,
 * so a {@link RequestCompletionEffect} doesn't need to know which DTO it came from (mirrors {@link
 * com.remotesupport.backend.web.requestdetails.RequestDetailsInput}'s own role at submission time;
 * provision-request-details ticket).
 *
 * <p>{@code newSmartphone}/{@code newSimCard}/{@code replacesSmartphoneId}/{@code
 * replacesSimCardId} are the older, full-form fields (fee-logging-and-provisioning ticket) that
 * only a legacy Request (one with no details of its own from submission) still reads. {@code
 * simCardNumber} is the new-style Provision SIM completion's own, much narrower field. Every field
 * is optional here — an effect decides which of its own type's fields are actually required.
 */
public record RequestCompletionInput(
    SmartphoneCreateRequest newSmartphone,
    SimCardCreateRequest newSimCard,
    UUID replacesSmartphoneId,
    UUID replacesSimCardId,
    String simCardNumber) {}
