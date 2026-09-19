package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.RequestRejectRequest;
import com.remotesupport.backend.dto.RequestResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.RequestRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
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
  private final UserRepository userRepository;

  public RequestByIdController(RequestRepository requestRepository, UserRepository userRepository) {
    this.requestRepository = requestRepository;
    this.userRepository = userRepository;
  }

  @PostMapping("/approve")
  public RequestResponse approve(
      @PathVariable UUID requestId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Request request = findPendingApproval(requestId, principal);

    request.setStatus(RequestStatus.SUBMITTED);
    decide(request, principal);
    requestRepository.save(request);

    AuditLog.requestApproved(request.getId(), request.getContract().getId(), principal.userId(), principal.tenantId());
    return RequestResponse.of(request);
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
    return RequestResponse.of(request);
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
