package com.remotesupport.backend.web.requestapproval;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.dto.RequestApprovalRequest;
import com.remotesupport.backend.dto.RequestCreateRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The one module that decides whether a Request needs approval and applies its approval payload,
 * shared by {@link com.remotesupport.backend.web.RequestController} (both creation paths' starting
 * status) and {@link com.remotesupport.backend.web.RequestByIdController} (approve). Neither
 * caller branches on {@code request.getType()}/{@code requestBody.type()} itself — they ask this
 * validator, which dispatches to the one {@link RequestApprovalHandler} that owns that type, if
 * any, exactly like {@code RequestDetailsValidator}/{@code ProvisioningService} already do for
 * details and completion.
 *
 * <p>The registry is built from every {@link RequestApprovalHandler} bean Spring finds on the
 * classpath, not a hand-maintained {@code switch} — so a later type with its own content-aware
 * approval rule means adding one new handler class, never editing this class or either caller.
 */
@Component
public class RequestApprovalValidator {

  private final Map<RequestType, RequestApprovalHandler> handlersByType;

  public RequestApprovalValidator(List<RequestApprovalHandler> handlers) {
    this.handlersByType =
        handlers.stream().collect(Collectors.toMap(RequestApprovalHandler::type, Function.identity()));
  }

  /**
   * Whether a Request of {@code type}, built from {@code requestBody}, must start at {@code
   * PENDING_APPROVAL}. A type with no registered handler falls back to {@link
   * RequestType#requiresApproval()} — the four types that are always gated, regardless of content.
   */
  public boolean requiresApproval(RequestType type, Contract contract, RequestCreateRequest requestBody) {
    RequestApprovalHandler handler = handlersByType.get(type);
    return handler == null ? type.requiresApproval() : handler.requiresApproval(contract, requestBody);
  }

  /**
   * Validates and applies {@code request.getType()}'s own approval payload, if it has one
   * registered; a no-op (empty summary) for every type that doesn't, since those approve with
   * nothing further to choose.
   */
  public String applyApproval(Request request, RequestApprovalRequest requestBody) {
    RequestApprovalHandler handler = handlersByType.get(request.getType());
    return handler == null ? "" : handler.applyApproval(request, requestBody);
  }
}
