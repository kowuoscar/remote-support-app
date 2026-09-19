package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.ReturnedUnit;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneOwner;
import com.remotesupport.backend.domain.Tester;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.RequestCreateRequest;
import com.remotesupport.backend.dto.RequestResponse;
import com.remotesupport.backend.dto.RequestStatusUpdateRequest;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.RequestRepository;
import com.remotesupport.backend.repository.ReturnedUnitRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.FleetAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.security.RequestAccessGuard;
import com.remotesupport.backend.web.completion.RequestCompletionInput;
import com.remotesupport.backend.web.requestdetails.RequestDetailsInput;
import com.remotesupport.backend.web.requestdetails.RequestDetailsValidator;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Requests raised against one Contract (tester-request-submission, agent-request-fulfillment
 * tickets). Create accepts two distinct authors — a Tester submitting their own Request, or the
 * Contract's own Agent logging one proactively on a Tester's behalf — enforced at the
 * request-matcher level in {@link com.remotesupport.backend.security.SecurityConfig} (role) and
 * re-checked (with Contract ownership) by {@link RequestAccessGuard}; viewing is scoped
 * per-Contract exactly like Fleet (Manager: any; Agent/Tester: only their own), so it reuses
 * {@link FleetAccessGuard#requireCanView} directly. A Tester's "see every Request raised by
 * anyone at my Client" visibility (spec.md user story 31) is a frontend concern: the client
 * fetches this same per-Contract endpoint once per Contract it holds and flattens the results,
 * exactly like the Fleet views already do (fleet-management ticket) — there is no separate
 * Client-wide endpoint.
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/requests")
public class RequestController {

  private final ContractRepository contractRepository;
  private final RequestRepository requestRepository;
  private final ReturnedUnitRepository returnedUnitRepository;
  private final SmartphoneRepository smartphoneRepository;
  private final TesterRepository testerRepository;
  private final UserRepository userRepository;
  private final FleetAccessGuard fleetAccessGuard;
  private final RequestAccessGuard requestAccessGuard;
  private final ProvisioningService provisioningService;
  private final RequestDetailsValidator requestDetailsValidator;

  public RequestController(
      ContractRepository contractRepository,
      RequestRepository requestRepository,
      ReturnedUnitRepository returnedUnitRepository,
      SmartphoneRepository smartphoneRepository,
      TesterRepository testerRepository,
      UserRepository userRepository,
      FleetAccessGuard fleetAccessGuard,
      RequestAccessGuard requestAccessGuard,
      ProvisioningService provisioningService,
      RequestDetailsValidator requestDetailsValidator) {
    this.contractRepository = contractRepository;
    this.requestRepository = requestRepository;
    this.returnedUnitRepository = returnedUnitRepository;
    this.smartphoneRepository = smartphoneRepository;
    this.testerRepository = testerRepository;
    this.userRepository = userRepository;
    this.fleetAccessGuard = fleetAccessGuard;
    this.requestAccessGuard = requestAccessGuard;
    this.provisioningService = provisioningService;
    this.requestDetailsValidator = requestDetailsValidator;
  }

  @PostMapping
  public ResponseEntity<RequestResponse> create(
      @PathVariable UUID contractId,
      @Valid @RequestBody RequestCreateRequest requestBody,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);

    String description = normalizeDescription(requestBody.description());
    requireDescriptionWhenOther(requestBody.type(), description);

    Request request = new Request();
    request.setId(UUID.randomUUID());
    request.setTenant(contract.getTenant());
    request.setContract(contract);
    request.setType(requestBody.type());
    request.setDescription(description);
    request.setCreatedAt(Instant.now());

    if ("AGENT".equals(principal.role())) {
      createAgentAuthored(contract, requestBody, principal, request);
    } else {
      createTesterAuthored(contract, requestBody, principal, request);
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(RequestResponse.of(request, returnedUnitsOf(request)));
  }

  /**
   * A Tester submitting their own Request (tester-request-submission ticket): always starts
   * {@code SUBMITTED}, always attributed to the caller's own Tester profile.
   */
  private void createTesterAuthored(
      Contract contract, RequestCreateRequest requestBody, AuthenticatedPrincipal principal, Request request) {
    requestAccessGuard.requireCanSubmit(contract, principal);

    Tester tester =
        testerRepository
            .findByUserId(principal.userId())
            .orElseThrow(() -> new AccessDeniedException("No Tester profile for this login"));

    request.setTester(tester);
    request.setRaisedByUser(tester.getUser());
    request.setAgentAuthored(false);
    request.setStatus(startingStatusFor(requestBody.type(), contract, requestBody));
    requestDetailsValidator.apply(contract, detailsInputOf(requestBody), request);
    requestRepository.save(request);

    AuditLog.requestSubmitted(
        request.getId(),
        contract.getId(),
        request.getType().name(),
        request.getDescription() != null,
        targetSmartphoneIdOf(request),
        targetSimCardIdOf(request),
        topupOptionIdOf(request),
        principal.userId(),
        principal.tenantId());
  }

  /**
   * The Contract's own Agent logging a Request proactively on a Tester's behalf
   * (agent-request-fulfillment ticket AC 3): the Agent names which of their Contract's Testers
   * it's raised for, and chooses whether it starts {@code SUBMITTED} or immediately {@code
   * COMPLETED}.
   */
  private void createAgentAuthored(
      Contract contract,
      RequestCreateRequest requestBody,
      AuthenticatedPrincipal principal,
      Request request) {
    requestAccessGuard.requireCanLogProactively(contract, principal);

    if (requestBody.testerId() == null) {
      throw new InvalidRequestException("testerId is required when an Agent logs a Request");
    }
    Tester tester =
        testerRepository
            .findByIdAndClientId(requestBody.testerId(), contract.getClient().getId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "No Tester with id " + requestBody.testerId() + " on this Contract's Client"));

    RequestStatus startingStatus =
        agentStartingStatusFor(requestBody.type(), requestBody.startingStatus(), contract, requestBody);

    User raisedByUser =
        userRepository
            .findById(principal.userId())
            .orElseThrow(() -> new AccessDeniedException("No login found for this Agent"));

    request.setTester(tester);
    request.setRaisedByUser(raisedByUser);
    request.setAgentAuthored(true);
    request.setStatus(startingStatus);

    requestDetailsValidator.apply(contract, detailsInputOf(requestBody), request);

    provisioningService.applyIfNeeded(contract, request, completionInputOf(requestBody), principal);

    requestRepository.save(request);

    AuditLog.requestLoggedByAgent(
        request.getId(),
        contract.getId(),
        request.getType().name(),
        startingStatus.name(),
        request.getDescription() != null,
        targetSmartphoneIdOf(request),
        targetSimCardIdOf(request),
        topupOptionIdOf(request),
        principal.userId(),
        principal.tenantId());
  }

  @GetMapping
  public List<RequestResponse> list(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    fleetAccessGuard.requireCanView(contract, principal);

    return requestRepository.findByContractIdOrderByCreatedAtAsc(contractId).stream()
        .map(request -> RequestResponse.of(request, returnedUnitsOf(request)))
        .toList();
  }

  @PatchMapping("/{requestId}/status")
  public RequestResponse updateStatus(
      @PathVariable UUID contractId,
      @PathVariable UUID requestId,
      @Valid @RequestBody RequestStatusUpdateRequest requestBody,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    requestAccessGuard.requireCanChangeStatus(contract, principal);

    Request request =
        requestRepository
            .findByIdAndContractId(requestId, contractId)
            .orElseThrow(() -> new NotFoundException("No request with id " + requestId));

    RequestStatus oldStatus = request.getStatus();
    RequestStatus newStatus = requestBody.status();
    if (!oldStatus.canTransitionTo(newStatus)) {
      throw new ConflictException("Cannot transition a Request from " + oldStatus + " to " + newStatus);
    }
    if (oldStatus == RequestStatus.PENDING_APPROVAL && newStatus != RequestStatus.CANCELLED) {
      // manager-approves-requests ticket, Constraints: "An Agent can never move a Request out of
      // Pending Approval except to Cancelled" — and a Manager approves/rejects through the
      // dedicated, identity-addressed actions (RequestByIdController), never through this general
      // status-change route, mirroring how Review Queue actions are addressed by invoice id.
      throw new InvalidRequestException(
          "Use the approve/reject actions to move a Request out of Pending Approval, other than cancelling it");
    }

    if (newStatus == RequestStatus.CANCELLED) {
      if (requestBody.cancellationReason() == null || requestBody.cancellationReason().isBlank()) {
        throw new InvalidRequestException("A cancellationReason is required when cancelling a Request");
      }
      request.setCancellationReason(requestBody.cancellationReason());
    }

    request.setStatus(newStatus);

    provisioningService.applyIfNeeded(
        contract,
        request,
        new RequestCompletionInput(
            requestBody.newSmartphone(),
            requestBody.newSimCard(),
            requestBody.replacesSmartphoneId(),
            requestBody.replacesSimCardId(),
            requestBody.simCardNumber(),
            requestBody.simCardCancellations()),
        principal);

    requestRepository.save(request);

    AuditLog.statusChanged(
        "Request",
        request.getId(),
        oldStatus.name(),
        newStatus.name(),
        principal.userId(),
        principal.tenantId());

    return RequestResponse.of(request, returnedUnitsOf(request));
  }

  /**
   * The units a {@code RETURN} Request names, for denormalizing into its {@link RequestResponse}
   * (return-client-owned-smartphones ticket AC: "shown on the Request in every Requests list") —
   * empty, and cheap, for every other type since {@code request_id} never matches any row.
   */
  private List<ReturnedUnit> returnedUnitsOf(Request request) {
    return returnedUnitRepository.findByRequestIdOrderByCreatedAtAsc(request.getId());
  }

  /**
   * A Tester-authored Request's starting status (request-types-and-flow spec, Lifecycle):
   * approval-required types always start {@link RequestStatus#PENDING_APPROVAL}; every other type
   * starts {@link RequestStatus#SUBMITTED} as before. {@code RETURN} is decided by content, not by
   * {@link RequestType#requiresApproval()} alone — see {@link #returnRequiresApproval}.
   */
  private RequestStatus startingStatusFor(RequestType type, Contract contract, RequestCreateRequest requestBody) {
    if (type == RequestType.RETURN) {
      return returnRequiresApproval(contract, requestBody) ? RequestStatus.PENDING_APPROVAL : RequestStatus.SUBMITTED;
    }
    return type.requiresApproval() ? RequestStatus.PENDING_APPROVAL : RequestStatus.SUBMITTED;
  }

  /**
   * An Agent-proactive Request's starting status: for one of the four approval-required types, or
   * a {@code RETURN} holding a company-owned unit, always {@link RequestStatus#PENDING_APPROVAL}
   * regardless of what {@code requestedStartingStatus} asked for (spec.md Lifecycle: "an Agent
   * logging one proactively can no longer start it at Submitted or Completed") — refused outright
   * rather than silently overridden, so a caller that still thinks it can choose finds out
   * immediately. Every other type/content keeps the existing choice between {@code SUBMITTED}
   * (default) and immediately {@code COMPLETED}.
   */
  private RequestStatus agentStartingStatusFor(
      RequestType type, RequestStatus requestedStartingStatus, Contract contract, RequestCreateRequest requestBody) {
    boolean requiresApproval =
        type == RequestType.RETURN ? returnRequiresApproval(contract, requestBody) : type.requiresApproval();
    if (requiresApproval) {
      if (requestedStartingStatus != null && requestedStartingStatus != RequestStatus.PENDING_APPROVAL) {
        throw new InvalidRequestException(
            "A " + type + " Request always starts Pending Approval; it cannot start " + requestedStartingStatus);
      }
      return RequestStatus.PENDING_APPROVAL;
    }
    RequestStatus startingStatus =
        requestedStartingStatus == null ? RequestStatus.SUBMITTED : requestedStartingStatus;
    if (startingStatus != RequestStatus.SUBMITTED && startingStatus != RequestStatus.COMPLETED) {
      throw new InvalidRequestException(
          "An Agent-logged Request can only start at SUBMITTED or COMPLETED, not " + startingStatus);
    }
    return startingStatus;
  }

  /**
   * Whether a {@code RETURN} Request's own named units hold at least one company-owned one — any
   * SIM Card (CONTEXT.md "Owner": always company-owned), or a company-owned Smartphone — which is
   * what sends the whole Request to Pending Approval (spec.md Solution: "Approval"), whoever raised
   * it. Looked up directly off the creation body's raw id lists rather than the {@link
   * ReturnedUnit} rows {@link com.remotesupport.backend.web.requestdetails.ReturnRequestDetailsHandler}
   * builds moments later — this only needs to know "is approval required", not build or validate
   * anything, and it must run before {@code request.setStatus} is set, ahead of that handler.
   * Ignored for every type but {@code RETURN}.
   */
  private boolean returnRequiresApproval(Contract contract, RequestCreateRequest requestBody) {
    List<UUID> simCardIds = requestBody.returnedSimCardIds();
    if (simCardIds != null && !simCardIds.isEmpty()) {
      return true;
    }
    List<UUID> smartphoneIds = requestBody.returnedSmartphoneIds();
    if (smartphoneIds == null) {
      return false;
    }
    for (UUID smartphoneId : smartphoneIds) {
      Smartphone smartphone = smartphoneRepository.findByIdAndContractId(smartphoneId, contract.getId()).orElse(null);
      if (smartphone != null && smartphone.getOwner() == SmartphoneOwner.COMPANY) {
        return true;
      }
    }
    return false;
  }

  private Contract findContract(UUID contractId, AuthenticatedPrincipal principal) {
    return contractRepository
        .findByIdAndTenantId(contractId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No contract with id " + contractId));
  }

  /** Pulls {@link RequestDetailsValidator}'s inputs out of the creation body — same on both paths. */
  static RequestDetailsInput detailsInputOf(RequestCreateRequest requestBody) {
    return new RequestDetailsInput(
        requestBody.targetSmartphoneId(),
        requestBody.targetSimCardId(),
        requestBody.topupOptionId(),
        requestBody.requestedModel(),
        requestBody.requestedFlavor(),
        requestBody.requestedCarrierId(),
        requestBody.requestedPostpaidPlanId(),
        requestBody.secondSimCardId(),
        requestBody.returnedSmartphoneIds(),
        requestBody.returnedSimCardIds());
  }

  /**
   * Pulls {@link ProvisioningService}'s completion inputs out of the creation body — only ever
   * meaningful when an Agent logs a Request that starts immediately {@code COMPLETED}.
   */
  static RequestCompletionInput completionInputOf(RequestCreateRequest requestBody) {
    return new RequestCompletionInput(
        requestBody.newSmartphone(),
        requestBody.newSimCard(),
        requestBody.replacesSmartphoneId(),
        requestBody.replacesSimCardId(),
        requestBody.simCardNumber(),
        // A RETURN holding a SIM Card always starts Pending Approval (see
        // #returnRequiresApproval), so it can never reach this immediately-Completed,
        // Agent-proactive path with a cancellation to record.
        List.of());
  }

  /**
   * The three audit fields reboot-and-topup-details ticket's Observability AC asks for ("the
   * target unit id and the Topup Option id"), read off {@code request} after {@link
   * RequestDetailsValidator} has set them — null for every type but the one that sets each.
   * Shared with {@link FeeController}'s proactive-Fee path, which logs the same auto-created
   * linking Request the same way.
   */
  static UUID targetSmartphoneIdOf(Request request) {
    return request.getTargetSmartphone() == null ? null : request.getTargetSmartphone().getId();
  }

  static UUID targetSimCardIdOf(Request request) {
    return request.getTargetSimCard() == null ? null : request.getTargetSimCard().getId();
  }

  static UUID topupOptionIdOf(Request request) {
    return request.getTopupOption() == null ? null : request.getTopupOption().getId();
  }

  /**
   * Blank-to-null, trimmed (request-types-and-flow spec, Details at submission;
   * other-replaces-repair ticket): every Request's optional description is stored this way, so
   * "no description" is always {@code null}, never an empty or whitespace-only string. Shared with
   * {@link FeeController}'s proactive-Fee path, which creates a Request the same way.
   */
  static String normalizeDescription(String description) {
    if (description == null) {
      return null;
    }
    String trimmed = description.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * The one AC both creation paths — Tester-submitted and Agent-proactive — and {@link
   * FeeController}'s proactive-Fee path enforce identically: "an Other Request is refused without
   * [a description]" (other-replaces-repair ticket AC).
   */
  static void requireDescriptionWhenOther(RequestType type, String normalizedDescription) {
    if (type == RequestType.OTHER && normalizedDescription == null) {
      throw new InvalidRequestException("A description is required for an Other Request");
    }
  }
}
