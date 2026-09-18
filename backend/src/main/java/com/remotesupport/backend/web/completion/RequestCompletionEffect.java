package com.remotesupport.backend.web.completion;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;

/**
 * The Fleet side-effect of completing one {@link RequestType} (request-types-and-flow spec,
 * "Fleet changes on completion, in one module, extending today's provisioning side-effect"). One
 * implementation per type, each a self-contained Spring bean — mirrors {@code
 * com.remotesupport.backend.web.requestdetails.RequestDetailsHandler}'s shape exactly, so that
 * `replace-requests`, `sim-swap-moves` and a later feature's Stock fulfilment each add one new
 * {@code @Component} class here to give their own type a completion effect, never editing this
 * interface, {@link com.remotesupport.backend.web.ProvisioningService} (the dispatcher), or any
 * other type's effect.
 */
public interface RequestCompletionEffect {

  /** Which {@link RequestType} this effect owns — the dispatcher's registry key. */
  RequestType type();

  /**
   * Applies this type's Fleet side-effect. Called only once {@code request.getStatus()} is
   * already {@code COMPLETED} and saved (or about to be), identically whether that happened via a
   * later status-transition PATCH, an Agent-proactive Request starting immediately Completed, or
   * a proactive Fee's auto-created linking Request. May set {@link Request#setCompletionNote} to
   * surface something the Agent should know about what just happened.
   */
  void apply(Contract contract, Request request, RequestCompletionInput input, AuthenticatedPrincipal principal);
}
