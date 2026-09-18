package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.web.completion.RequestCompletionEffect;
import com.remotesupport.backend.web.completion.RequestCompletionInput;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The one module that applies every Request type's own Fleet side-effect when it completes
 * (request-types-and-flow spec, "Fleet changes on completion, in one module, extending today's
 * provisioning side-effect"; originally the {@code PROVISION_SMARTPHONE}/{@code PROVISION_SIM}
 * logic itself, per fee-logging-and-provisioning). Shared by every path a Request can reach {@code
 * COMPLETED} through: {@link RequestController#updateStatus} (Tester-raised, later completed by
 * the Agent), {@link RequestController#create} when an Agent logs one proactively starting
 * immediately {@code COMPLETED}, and {@link FeeController} when a proactive Fee's own auto-created
 * linking Request is a completion-effect type.
 *
 * <p>The registry is built from every {@link RequestCompletionEffect} bean Spring finds on the
 * classpath, not a hand-maintained {@code switch} — mirrors {@code
 * com.remotesupport.backend.web.requestdetails.RequestDetailsValidator}'s own shape exactly, so a
 * later type (`replace-requests`, `sim-swap-moves`, and a later feature's Stock fulfilment) adds
 * one new effect class, never editing this dispatcher, any existing effect, or either controller.
 */
@Component
public class ProvisioningService {

  private final Map<RequestType, RequestCompletionEffect> effectsByType;

  public ProvisioningService(List<RequestCompletionEffect> effects) {
    this.effectsByType = effects.stream().collect(Collectors.toMap(RequestCompletionEffect::type, Function.identity()));
  }

  /**
   * Applies {@code request.getType()}'s own completion effect if {@code request} has just reached
   * {@code COMPLETED}; a no-op for every other type/status combination (e.g. a Topup or Other being
   * completed, or a Request moving to a non-terminal status), and for a type with no effect
   * registered yet.
   */
  public void applyIfNeeded(
      Contract contract, Request request, RequestCompletionInput input, AuthenticatedPrincipal principal) {
    if (request.getStatus() != RequestStatus.COMPLETED) {
      return;
    }
    RequestCompletionEffect effect = effectsByType.get(request.getType());
    if (effect != null) {
      effect.apply(contract, request, input, principal);
    }
  }
}
