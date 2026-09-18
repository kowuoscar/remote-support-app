package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Fee;
import com.remotesupport.backend.domain.FeeType;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.Tester;
import com.remotesupport.backend.domain.TopupOption;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.FeeCreateRequest;
import com.remotesupport.backend.dto.FeeResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.repository.RequestRepository;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.repository.TopupOptionRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.FleetAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.security.RequestAccessGuard;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fees logged against a Contract, always traced back to a Request (spec.md Solution's Fee entity;
 * fee-logging-and-provisioning ticket). Nested under the Contract exactly like Requests/Fleet.
 * Create accepts two shapes in a single call, matching the ticket's "single API call/form" note:
 *
 * <ul>
 *   <li>{@code requestId} set — the Fee traces to that existing Request. Rejected 400 when that
 *       Request's type can never carry a Fee ({@link FeeType#requestTypeCanCarryFee}: Reboot, or
 *       a like-for-like SIM Swap that never became a Provision SIM Fee) or when the given {@code
 *       feeType} doesn't match the Request's own type.
 *   <li>{@code requestId} null — a proactive Fee (AC: "A Fee the Agent logs with no pre-existing
 *       Request auto-creates its linking Request (completed, proactive)"). The linking Request is
 *       created here, immediately {@code COMPLETED}, agent-authored, reusing the exact same shape
 *       {@link RequestController#createAgentAuthored} uses for a proactive Request — the only
 *       difference is this path always knows its own type ({@code feeType}) and always starts
 *       Completed, so it doesn't go through that endpoint. There is no other way to reach this
 *       branch: {@link FeeType} structurally excludes Reboot and SIM Swap, so a proactive Fee can
 *       never auto-create a Request of either type.
 * </ul>
 *
 * Both branches end the same way: a {@link Fee} row is always attached to a saved {@link Request}
 * before the response is built — there is no code path in this controller (or anywhere else) that
 * persists a {@code Fee} without one; {@link Fee#request} is non-nullable at both the JPA and
 * database level as the final backstop.
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/fees")
public class FeeController {

  private final ContractRepository contractRepository;
  private final RequestRepository requestRepository;
  private final FeeRepository feeRepository;
  private final TesterRepository testerRepository;
  private final UserRepository userRepository;
  private final FleetAccessGuard fleetAccessGuard;
  private final RequestAccessGuard requestAccessGuard;
  private final ProvisioningService provisioningService;
  private final TopupOptionRepository topupOptionRepository;

  public FeeController(
      ContractRepository contractRepository,
      RequestRepository requestRepository,
      FeeRepository feeRepository,
      TesterRepository testerRepository,
      UserRepository userRepository,
      FleetAccessGuard fleetAccessGuard,
      RequestAccessGuard requestAccessGuard,
      ProvisioningService provisioningService,
      TopupOptionRepository topupOptionRepository) {
    this.contractRepository = contractRepository;
    this.requestRepository = requestRepository;
    this.feeRepository = feeRepository;
    this.testerRepository = testerRepository;
    this.userRepository = userRepository;
    this.fleetAccessGuard = fleetAccessGuard;
    this.requestAccessGuard = requestAccessGuard;
    this.provisioningService = provisioningService;
    this.topupOptionRepository = topupOptionRepository;
  }

  @PostMapping
  public ResponseEntity<FeeResponse> create(
      @PathVariable UUID contractId,
      @Valid @RequestBody FeeCreateRequest requestBody,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    // Only the Contract's own Agent (or a Manager) may log a Fee against it (spec.md Access
    // control: "Agent: full CRUD on ... Fees ... within their own Contracts") — identical shape
    // to logging a Request proactively, so it's reused rather than re-implemented.
    requestAccessGuard.requireCanLogProactively(contract, principal);
    // Checked before anything is written, so a refused Option never leaves a linking Request.
    TopupOption topupOption = findPickableTopupOption(contract, requestBody);

    Request request =
        requestBody.requestId() == null
            ? createProactiveLinkingRequest(contract, requestBody, principal)
            : findEligibleExistingRequest(contract, requestBody);

    Fee fee = new Fee();
    fee.setId(UUID.randomUUID());
    fee.setTenant(contract.getTenant());
    fee.setContract(contract);
    fee.setRequest(request);
    fee.setFeeType(requestBody.feeType());
    fee.setAmount(requestBody.amount());
    fee.setCurrency(contract.getCurrency());
    fee.setDescription(requestBody.description());
    fee.setTopupOption(topupOption);
    fee.setBillingMonth(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1));
    fee.setCreatedAt(Instant.now());
    feeRepository.save(fee);

    AuditLog.feeLogged(
        fee.getId(),
        contract.getId(),
        request.getId(),
        fee.getFeeType().name(),
        fee.getAmount(),
        topupOption == null ? null : topupOption.getId(),
        principal.userId(),
        principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED).body(FeeResponse.of(fee));
  }

  /**
   * The Topup Option a Topup Fee names, or {@code null} when it names none (topup-fee-from-option
   * ticket). Refused unless the Fee is a Topup Fee and the Option is active, of an active Carrier
   * of this Contract's tenant and Country. An Option of another tenant is refused exactly like an
   * unknown one, so its existence never leaks.
   */
  private TopupOption findPickableTopupOption(Contract contract, FeeCreateRequest requestBody) {
    if (requestBody.topupOptionId() == null) {
      return null;
    }
    if (requestBody.feeType() != FeeType.TOPUP) {
      throw new InvalidRequestException("Only a Topup Fee can name a Topup Option");
    }
    TopupOption option =
        topupOptionRepository
            .findById(requestBody.topupOptionId())
            .filter(o -> o.getCarrier().getTenant().getId().equals(contract.getTenant().getId()))
            .orElseThrow(
                () -> new InvalidRequestException("No Topup Option with id " + requestBody.topupOptionId()));
    Carrier carrier = option.getCarrier();
    if (carrier.getCountry() != contract.getAgent().getCountry()) {
      throw new InvalidRequestException("This Topup Option belongs to another Country's Carrier");
    }
    if (option.isArchived() || carrier.isArchived()) {
      throw new InvalidRequestException("This Topup Option, or its Carrier, is archived");
    }
    return option;
  }

  /** The {@code requestId} branch: the named Request must exist on this Contract and allow a Fee. */
  private Request findEligibleExistingRequest(Contract contract, FeeCreateRequest requestBody) {
    Request request =
        requestRepository
            .findByIdAndContractId(requestBody.requestId(), contract.getId())
            .orElseThrow(
                () -> new NotFoundException("No request with id " + requestBody.requestId()));

    if (!FeeType.requestTypeCanCarryFee(request.getType())) {
      throw new InvalidRequestException(
          "A "
              + request.getType()
              + " Request can never carry a Fee — a Reboot never does, and a like-for-like SIM"
              + " Swap only does when it required provisioning a new physical SIM, logged as a"
              + " proactive Provision SIM Fee instead");
    }
    if (requestBody.feeType().toRequestType() != request.getType()) {
      throw new InvalidRequestException(
          "feeType " + requestBody.feeType() + " does not match this Request's type " + request.getType());
    }
    return request;
  }

  /** The proactive branch: auto-creates the linking Request (agent-request-fulfillment shape). */
  private Request createProactiveLinkingRequest(
      Contract contract, FeeCreateRequest requestBody, AuthenticatedPrincipal principal) {
    if (requestBody.testerId() == null) {
      throw new InvalidRequestException("testerId is required when logging a Fee with no pre-existing Request");
    }
    Tester tester =
        testerRepository
            .findByIdAndClientId(requestBody.testerId(), contract.getClient().getId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "No Tester with id " + requestBody.testerId() + " on this Contract's Client"));
    User raisedByUser =
        userRepository
            .findById(principal.userId())
            .orElseThrow(() -> new AccessDeniedException("No login found for this Agent"));

    // The auto-created linking Request carries the same description the Agent gave the Fee
    // (request-types-and-flow spec, Details at submission; other-replaces-repair ticket): it's one
    // submission producing both rows, so an Other proactive Fee is refused the same way a Tester's
    // Other Request submission is — no description, no Request.
    RequestType requestType = requestBody.feeType().toRequestType();
    String description = RequestController.normalizeDescription(requestBody.description());
    RequestController.requireDescriptionWhenOther(requestType, description);

    Request request = new Request();
    request.setId(UUID.randomUUID());
    request.setTenant(contract.getTenant());
    request.setContract(contract);
    request.setType(requestType);
    request.setDescription(description);
    request.setTester(tester);
    request.setRaisedByUser(raisedByUser);
    request.setAgentAuthored(true);
    request.setStatus(RequestStatus.COMPLETED);
    request.setCreatedAt(Instant.now());

    provisioningService.applyIfNeeded(
        contract,
        request,
        requestBody.newSmartphone(),
        requestBody.newSimCard(),
        requestBody.replacesSmartphoneId(),
        requestBody.replacesSimCardId(),
        principal);

    requestRepository.save(request);

    AuditLog.requestLoggedByAgent(
        request.getId(),
        contract.getId(),
        request.getType().name(),
        request.getStatus().name(),
        request.getDescription() != null,
        principal.userId(),
        principal.tenantId());

    return request;
  }

  @GetMapping
  public List<FeeResponse> list(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    fleetAccessGuard.requireCanView(contract, principal);

    return feeRepository.findByContractIdOrderByCreatedAtAsc(contractId).stream()
        .map(FeeResponse::of)
        .toList();
  }

  private Contract findContract(UUID contractId, AuthenticatedPrincipal principal) {
    return contractRepository
        .findByIdAndTenantId(contractId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No contract with id " + contractId));
  }
}
