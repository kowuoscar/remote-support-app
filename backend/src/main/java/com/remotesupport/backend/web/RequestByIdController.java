package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Disposition;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.ReturnedUnit;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.RequestApprovalRequest;
import com.remotesupport.backend.dto.RequestRejectRequest;
import com.remotesupport.backend.dto.RequestResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.RequestRepository;
import com.remotesupport.backend.repository.ReturnedUnitRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  public RequestByIdController(
      RequestRepository requestRepository,
      ReturnedUnitRepository returnedUnitRepository,
      UserRepository userRepository) {
    this.requestRepository = requestRepository;
    this.returnedUnitRepository = returnedUnitRepository;
    this.userRepository = userRepository;
  }

  @PostMapping("/approve")
  public RequestResponse approve(
      @PathVariable UUID requestId,
      // Optional: a Provision/Replace Request approves with no input at all. A RETURN Request
      // holding a company-owned unit needs its own per-unit Disposition here instead
      // (manager-decides-return-disposition ticket, spec.md Solution's Disposition table) — see
      // RequestApprovalRequest's own Javadoc for why this stayed one shape rather than a new route.
      @RequestBody(required = false) RequestApprovalRequest requestBody,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Request request = findPendingApproval(requestId, principal);

    String dispositionsChosen =
        request.getType() == RequestType.RETURN ? applyReturnDispositions(request, requestBody) : "";

    request.setStatus(RequestStatus.SUBMITTED);
    decide(request, principal);
    requestRepository.save(request);

    AuditLog.requestApproved(
        request.getId(), request.getContract().getId(), dispositionsChosen, principal.userId(), principal.tenantId());
    return RequestResponse.of(request, returnedUnitsOf(request));
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
    return RequestResponse.of(request, returnedUnitsOf(request));
  }

  /**
   * Applies a {@code RETURN} Request's Manager-chosen Dispositions at the moment of approval
   * (spec.md Solution: "Approving a Return requires a Disposition for every company-owned unit in
   * the same action; without them the approval is refused") — a Client-owned unit already carries
   * its fixed {@link Disposition#POSTED_TO_CLIENT} from submission ({@code
   * ReturnRequestDetailsHandler}) and can't be named here (ticket AC: "Dispositions can't be
   * changed after approval"); every other named unit must be, exactly once, with a Disposition
   * that fits its own kind — a Smartphone only {@link Disposition#POSTED_TO_COMPANY}, a SIM Card
   * only {@link Disposition#CANCELLED} (the only choices until the {@code agent-stock} ticket adds
   * {@link Disposition#KEPT_IN_STOCK} for either kind). Returns a log-friendly summary of what was
   * chosen, for {@link AuditLog#requestApproved}.
   */
  private String applyReturnDispositions(Request request, RequestApprovalRequest requestBody) {
    List<ReturnedUnit> units = returnedUnitRepository.findByRequestIdOrderByCreatedAtAsc(request.getId());

    Map<UUID, Disposition> chosen = new HashMap<>();
    if (requestBody != null && requestBody.dispositions() != null) {
      for (RequestApprovalRequest.UnitDisposition entry : requestBody.dispositions()) {
        chosen.put(entry.returnedUnitId(), entry.disposition());
      }
    }

    List<ReturnedUnit> toSave = new ArrayList<>();
    List<String> logged = new ArrayList<>();
    for (ReturnedUnit unit : units) {
      if (unit.getDisposition() != null) {
        if (chosen.containsKey(unit.getId())) {
          throw new InvalidRequestException(
              "The unit " + unit.getId() + " already has a Disposition and it cannot be changed");
        }
        continue;
      }

      Disposition disposition = chosen.get(unit.getId());
      if (disposition == null) {
        throw new InvalidRequestException("A Disposition is required for every company-owned unit, including " + unit.getId());
      }
      if (unit.getSmartphone() != null && disposition != Disposition.POSTED_TO_COMPANY) {
        throw new InvalidRequestException(
            "A company-owned Smartphone's Disposition must be Posted to company, not " + disposition);
      }
      if (unit.getSimCard() != null && disposition != Disposition.CANCELLED) {
        throw new InvalidRequestException("A SIM Card's Disposition must be Cancelled, not " + disposition);
      }

      unit.setDisposition(disposition);
      toSave.add(unit);
      logged.add(unit.getId() + "=" + disposition.name());
    }
    returnedUnitRepository.saveAll(toSave);
    return String.join(",", logged);
  }

  /** Mirrors {@link RequestController#returnedUnitsOf} — every unit a {@code RETURN} names. */
  private List<ReturnedUnit> returnedUnitsOf(Request request) {
    return returnedUnitRepository.findByRequestIdOrderByCreatedAtAsc(request.getId());
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
