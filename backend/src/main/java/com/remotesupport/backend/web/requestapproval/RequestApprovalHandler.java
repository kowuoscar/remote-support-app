package com.remotesupport.backend.web.requestapproval;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.dto.RequestApprovalRequest;
import com.remotesupport.backend.dto.RequestCreateRequest;

/**
 * A {@link RequestType}'s own content-aware approval rule (returns-and-agent-stock spec, Solution:
 * "Approval"; review finding on this feature's finisher pass). Mirrors {@code RequestDetailsHandler}
 * and {@code RequestCompletionEffect}'s own shape exactly: one implementation per type that needs
 * one, each a self-contained Spring bean; {@link RequestApprovalValidator} discovers every
 * implementation on the classpath by autowiring {@code List<RequestApprovalHandler>}, so a later
 * type with its own content-aware approval rule is added by adding one new {@code @Component}
 * class here — never by editing this interface, the validator, or {@code RequestController}/{@code
 * RequestByIdController}.
 *
 * <p>Only {@code RETURN} needs one today: whether it requires approval depends on which units it
 * names (any company-owned one), not on its type alone the way the four always-gated types are
 * ({@link RequestType#requiresApproval()} still covers those, unconditionally — see that method's
 * own Javadoc for why it wasn't folded in here). A type with no registered handler falls back to
 * {@link RequestType#requiresApproval()} for the "does it need approval" question, and approves
 * with no extra payload to validate for the "apply the approval payload" one.
 */
public interface RequestApprovalHandler {

  /** Which {@link RequestType} this handler owns — {@link RequestApprovalValidator}'s registry key. */
  RequestType type();

  /**
   * Whether a Request of this type, built from {@code requestBody}, must start at {@code
   * PENDING_APPROVAL} — checked before the Request's status is set, on both the Tester and the
   * Agent-proactive creation path, ahead of the type's own {@code RequestDetailsHandler} building
   * anything durable.
   */
  boolean requiresApproval(Contract contract, RequestCreateRequest requestBody);

  /**
   * Validates and applies {@code requestBody}'s own approval payload against {@code request}
   * (already confirmed {@code PENDING_APPROVAL} by the caller) — e.g. a {@code RETURN}'s
   * per-unit Dispositions. Throws {@link com.remotesupport.backend.web.InvalidRequestException}
   * for a missing, already-decided, or ill-fitting choice, the same exception every other
   * cross-field business rule in this codebase uses. Returns a log-friendly summary for {@link
   * com.remotesupport.backend.logging.AuditLog#requestApproved} — empty when there was nothing to
   * choose.
   */
  String applyApproval(Request request, RequestApprovalRequest requestBody);
}
