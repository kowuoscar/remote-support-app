package com.remotesupport.backend.web.requestdetails;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The one module that validates and stores every Request type's own details, shared by both
 * creation paths (request-types-and-flow spec, Details at submission; reboot-and-topup-details
 * ticket): {@link com.remotesupport.backend.web.RequestController#create} (Tester-authored and
 * Agent-proactive) and {@link com.remotesupport.backend.web.FeeController}'s proactive-Fee
 * auto-created linking Request. Neither caller branches on {@code request.getType()} itself —
 * they hand this validator the Request and whatever detail fields the body carried, and it
 * dispatches to the one {@link RequestDetailsHandler} that owns that type, if any.
 *
 * <p>The registry is built from every {@link RequestDetailsHandler} bean Spring finds on the
 * classpath, not a hand-maintained {@code switch} — so adding a type's details later
 * (provision-request-details, replace-requests, sim-swap-moves tickets) means adding one new
 * handler class, never editing this class, {@link RequestDetailsHandler}, or either caller.
 */
@Component
public class RequestDetailsValidator {

  private final Map<RequestType, RequestDetailsHandler> handlersByType;

  public RequestDetailsValidator(List<RequestDetailsHandler> handlers) {
    this.handlersByType =
        handlers.stream().collect(Collectors.toMap(RequestDetailsHandler::type, Function.identity()));
  }

  /**
   * Applies {@code request.getType()}'s own handler, if one is registered; a no-op for any type
   * that doesn't have one yet (its details, if any, are validated wherever they already were).
   */
  public void apply(Contract contract, RequestDetailsInput input, Request request) {
    RequestDetailsHandler handler = handlersByType.get(request.getType());
    if (handler != null) {
      handler.apply(contract, input, request);
    }
  }
}
