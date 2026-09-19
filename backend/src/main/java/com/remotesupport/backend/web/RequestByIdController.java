package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.RequestApprovalRequest;
import com.remotesupport.backend.dto.RequestRejectRequest;
import com.remotesupport.backend.dto.RequestResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.RequestRepository;
import com.remotesupport.backend.repository.ReturnedUnitRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.web.requestapproval.RequestApprovalValidator;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Request addressed by its own id (request-types-and-flow spec, Manager approval: "Approve and
 * reject are addressed by the Request's own identity, the way Review Queue actions are";
 * manager-approves-requests ticket): the Manager's approve and reject, for a Request at Pending
 * Approval on any Contract. Mirrors {@link AgentInvoiceByIdController}'s shape — the lookup is
 * scoped to the caller's tenant, so an unknown id and another tenant's id are the same 404 and
 * existence never leaks. Manager-only at the matcher level (SecurityConfig).
 *
 * <p>Deliberately separate from {@link RequestController#updateStatus}, which refuses to move a
 * Request out of Pending Approval at all except to Cancelled — approving/rejecting only ever
 * happens here, so there is exactly one place that records the decision (who and when).
 */
@RestController
@RequestMapping("/api/requests/{requestId}")
public class RequestByIdController {

  private final RequestRepository requestRepository;
  private final ReturnedUnitRepository returnedUnitRepository;
  private final UserRepository userRepository;
  private final RequestApprovalValidator requestApprovalValidator;

  public RequestByIdController(
      RequestRepository requestRepository,
      ReturnedUnitRepository returnedUnitRepository,
      UserRepository userRepository,
      RequestApprovalValidator requestApprovalValidator) {
    this.requestRepository = requestRepository;
    this.returnedUnitRepository = returnedUnitRepository;
    this.userRepository = userRepository;
    this.requestApprovalValidator = requestApprovalValidator;
  }

  @PostMapping("/approve")
  public RequestResponse approve(
      @PathVariable UUID requestId,
      // Optional: a Provision/Replace Request approves with no input at all. A type with its own
      // approval payload (e.g. a RETURN's per-unit Dispositions — manager-decides-return-disposition
      // ticket, spec.md Solution's Disposition table) reads it here instead, via its own {@link
      // RequestApprovalValidator}-registered handler — see RequestApprovalRequest's own Javadoc for
      // why this stayed one shape rather than a new route.
      @RequestBody(required = false) RequestApprovalRequest requestBody,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Request request = findPendingApproval(requestId, principal);

    String dispositionsChosen = requestApprovalValidator.applyApproval(request, requestBody);

    request.setStatus(RequestStatus.SUBMITTED);
    decide(request, principal);
    requestRepository.save(request);

    AuditLog.requestApproved(
        request.getId(), request.getContract().getId(), dispositionsChosen, principal.userId(), principal.tenantId());
    return RequestResponse.of(request, returnedUnitRepository.forRequest(request));
  }

  @PostMapping("/reject")
  public RequestResponse reject(
      @PathVariable UUID requestId,
      @Valid @RequestBody RequestRejectRequest requestBody,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Request request = findPendingApproval(requestId, principal);

    request.setStatus(RequestStatus.REJECTED);
    request.setRejectionReason(requestBody.reason());
    decide(request, principal);
    requestRepository.save(request);

    AuditLog.requestRejected(
        request.getId(), request.getContract().getId(), true, principal.userId(), principal.tenantId());
    return RequestResponse.of(request, returnedUnitRepository.forRequest(request));
  }

  private void decide(Request request, AuthenticatedPrincipal principal) {
    User decidedBy =
        userRepository
            .findById(principal.userId())
            .orElseThrow(() -> new AccessDeniedException("No login found for this Manager"));
    request.setDecidedByUser(decidedBy);
    request.setDecidedAt(Instant.now());
  }

  private Request findPendingApproval(UUID requestId, AuthenticatedPrincipal principal) {
    Request request =
        requestRepository
            .findByIdAndTenantId(requestId, principal.tenantId())
            .orElseThrow(() -> new NotFoundException("No request with id " + requestId));
    if (request.getStatus() != RequestStatus.PENDING_APPROVAL) {
      throw new ConflictException(
          "Cannot decide a Request that is not Pending Approval (current status: " + request.getStatus() + ")");
    }
    return request;
  }
}
