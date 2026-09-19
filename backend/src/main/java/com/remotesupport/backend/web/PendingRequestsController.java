package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.ReturnedUnit;
import com.remotesupport.backend.dto.PendingRequestItemResponse;
import com.remotesupport.backend.repository.RequestRepository;
import com.remotesupport.backend.repository.ReturnedUnitRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Manager's Pending Requests page (CONTEXT.md "Pending Requests"; request-types-and-flow spec,
 * Manager approval; manager-approves-requests ticket): every Request at Pending Approval across
 * every Contract in the caller's tenant, longest-waiting first with the Request id as a stable
 * tiebreak — the Request-side mirror of {@link ReviewQueueController}. Manager-only at the matcher
 * level (SecurityConfig). Read-only: it never creates or changes a Request; approve/reject live in
 * {@link RequestByIdController}.
 */
@RestController
public class PendingRequestsController {

  private static final Comparator<PendingRequestItemResponse> LONGEST_WAITING_FIRST =
      Comparator.comparing(PendingRequestItemResponse::waitingSince)
          .thenComparing(item -> item.request().id().toString());

  private final RequestRepository requestRepository;
  private final ReturnedUnitRepository returnedUnitRepository;

  public PendingRequestsController(RequestRepository requestRepository, ReturnedUnitRepository returnedUnitRepository) {
    this.requestRepository = requestRepository;
    this.returnedUnitRepository = returnedUnitRepository;
  }

  @GetMapping("/api/pending-requests")
  public List<PendingRequestItemResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return requestRepository.findByTenantIdAndStatus(principal.tenantId(), RequestStatus.PENDING_APPROVAL).stream()
        .map(request -> PendingRequestItemResponse.of(request, returnedUnitsOf(request)))
        .sorted(LONGEST_WAITING_FIRST)
        .toList();
  }

  /** Mirrors {@link RequestController#returnedUnitsOf} — cheap and empty for every non-Return type. */
  private List<ReturnedUnit> returnedUnitsOf(Request request) {
    return returnedUnitRepository.findByRequestIdOrderByCreatedAtAsc(request.getId());
  }
}
