package com.remotesupport.backend.web.requestdetails;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;

/**
 * Validates and stores one {@link RequestType}'s own details (request-types-and-flow spec,
 * Details at submission; reboot-and-topup-details ticket). One implementation per type, each a
 * self-contained Spring bean: {@link RequestDetailsValidator} discovers every implementation on
 * the classpath by autowiring {@code List<RequestDetailsHandler>}, so a later ticket adds a type
 * by adding one new {@code @Component} class here — never by editing this interface, the
 * validator, or any existing handler. A type with no handler registered (every type but Reboot
 * and Topup, as of this ticket) is simply not looked up; {@link RequestDetailsValidator#apply}
 * no-ops for it, leaving that type's existing validation (if any) exactly where it already lives.
 */
public interface RequestDetailsHandler {

  /** Which {@link RequestType} this handler owns — {@link RequestDetailsValidator}'s registry key. */
  RequestType type();

  /**
   * Validates {@code input} against {@code contract}'s Fleet/catalog and sets the corresponding
   * field(s) on {@code request}. Throws {@link com.remotesupport.backend.web.InvalidRequestException}
   * for a missing, foreign-Contract, retired, or otherwise unusable reference — the same
   * exception every other cross-field business rule in this codebase uses (see {@code
   * SimCardFactory}). Called after {@code request.getType()}/{@code getDescription()} are already
   * set, before the Request is saved, identically on the Tester path, the Agent-proactive path,
   * and a proactive Fee's auto-created linking Request.
   */
  void apply(Contract contract, RequestDetailsInput input, Request request);
}
