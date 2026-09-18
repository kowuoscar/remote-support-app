package com.remotesupport.backend.web.requestdetails;

import java.util.UUID;

/**
 * The type-specific fields a Request's creation body can carry, gathered from either {@link
 * com.remotesupport.backend.dto.RequestCreateRequest} (Tester or Agent-proactive path) or {@link
 * com.remotesupport.backend.dto.FeeCreateRequest} (a proactive Fee's own auto-created linking
 * Request) into one shape, so a {@link RequestDetailsHandler} doesn't need to know which DTO it
 * came from (request-types-and-flow spec, "Details at submission ... validated by one module that
 * both the Tester and the Agent-proactive paths call"; reboot-and-topup-details ticket). {@code
 * description} is deliberately absent: it's already set on the {@link
 * com.remotesupport.backend.domain.Request} being built by the time a handler runs, so a handler
 * reads {@code request.getDescription()} directly rather than through this record.
 *
 * <p>Every field is optional here — a handler decides which of its own type's fields are actually
 * required, and ignores the rest. Adding a new type's detail field (e.g. a future ticket's
 * requested model) means adding a field here plus the one handler that reads it, never editing
 * every existing handler.
 */
public record RequestDetailsInput(UUID targetSmartphoneId, UUID targetSimCardId, UUID topupOptionId) {}
