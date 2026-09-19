package com.remotesupport.backend.web.completion;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.dto.SimCardCreateRequest;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.web.ConflictException;
import com.remotesupport.backend.web.InvalidRequestException;
import com.remotesupport.backend.web.NotFoundException;
import com.remotesupport.backend.web.SimCardFactory;
import com.remotesupport.backend.web.SimInstallationService;
import com.remotesupport.backend.web.StockFulfilmentService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Completing a {@link RequestType#PROVISION_SIM} Request (provision-request-details ticket AC:
 * "Completing a Provision SIM asks only for the SIM number; Carrier, flavor, Plan and monthly fee
 * come from the Request, even if the Carrier or Plan was archived after submission"). A Request
 * that already carries its own {@code requestedFlavor} from submission (every one submitted since
 * this ticket) takes this new, number-only path; one from before this ticket ({@code
 * requestedFlavor} null) falls back to the previous full form (ticket AC: "completes through the
 * previous full form").
 *
 * <p>{@code fulfillFromStockSimCardId} (fulfil-from-stock ticket): only offered on the new-style
 * path — a legacy Request has no {@code requestedCarrier}/{@code requestedFlavor}/{@code
 * requestedPostpaidPlan} of its own to match a Stock SIM Card against, so Stock fulfilment isn't
 * offered there.
 */
@Component
public class ProvisionSimCompletionEffect implements RequestCompletionEffect {

  private final SimCardRepository simCardRepository;
  private final SimCardFactory simCardFactory;
  private final SimInstallationService simInstallationService;
  private final StockFulfilmentService stockFulfilmentService;

  public ProvisionSimCompletionEffect(
      SimCardRepository simCardRepository,
      SimCardFactory simCardFactory,
      SimInstallationService simInstallationService,
      StockFulfilmentService stockFulfilmentService) {
    this.simCardRepository = simCardRepository;
    this.simCardFactory = simCardFactory;
    this.simInstallationService = simInstallationService;
    this.stockFulfilmentService = stockFulfilmentService;
  }

  @Override
  public RequestType type() {
    return RequestType.PROVISION_SIM;
  }

  @Override
  public void apply(Contract contract, Request request, RequestCompletionInput input, AuthenticatedPrincipal principal) {
    if (request.getRequestedFlavor() != null) {
      completeFromRequestDetails(contract, request, input, principal);
      return;
    }

    // Legacy fallback: a Request submitted before this ticket carries no requestedFlavor, so it
    // completes exactly the way it always did — the full SimCardCreateRequest form, optionally
    // retiring a named unit.
    SimCardCreateRequest newSimCard = input.newSimCard();
    if (newSimCard == null) {
      throw new InvalidRequestException("newSimCard details are required to complete a Provision SIM request");
    }
    SimCard simCard = simCardFactory.create(contract, newSimCard);
    logProvisioned(contract, request, simCard, principal);

    request.setReplacesSimCardId(input.replacesSimCardId());
    if (input.replacesSimCardId() != null) {
      retireReplacedSimCard(contract, request.getId(), input.replacesSimCardId(), principal);
    }
  }

  private void completeFromRequestDetails(
      Contract contract, Request request, RequestCompletionInput input, AuthenticatedPrincipal principal) {
    SimCard simCard;
    if (input.fulfillFromStockSimCardId() != null) {
      // fulfil-from-stock ticket: the Stock SIM Card keeps its own number and monthly fee (ticket
      // AC) — only its Contract/holding Agent change, so no new row and no number is asked.
      simCard =
          stockFulfilmentService.takeMatchingSimCardFromStock(
              contract,
              input.fulfillFromStockSimCardId(),
              request.getRequestedCarrier(),
              request.getRequestedFlavor(),
              request.getRequestedPostpaidPlan(),
              request.getId(),
              principal);
    } else {
      String simCardNumber = input.simCardNumber();
      if (!StringUtils.hasText(simCardNumber)) {
        throw new InvalidRequestException("A simCardNumber is required to complete a Provision SIM request");
      }

      simCard = new SimCard();
      simCard.setId(UUID.randomUUID());
      simCard.setTenant(contract.getTenant());
      simCard.setContract(contract);
      simCard.setNumber(simCardNumber.trim());
      // Carrier, flavor and Plan come from the Request as it was at submission — never
      // re-validated here, so a Carrier or Plan archived afterwards still lets this Request
      // complete (spec.md: "archiving hides an entry from pickers, it never invalidates a record
      // that already uses it").
      simCard.setCarrier(request.getRequestedCarrier());
      simCard.setFlavor(request.getRequestedFlavor());
      simCard.setPostpaidPlan(request.getRequestedPostpaidPlan());
      simCard.setMonthlyFeeAmount(
          request.getRequestedPostpaidPlan() == null ? null : request.getRequestedPostpaidPlan().getPrice());
      simCard.setStatus(SimCardStatus.ACTIVE);
      simCard.setCreatedAt(Instant.now());
      simCardRepository.save(simCard);

      logProvisioned(contract, request, simCard, principal);
    }

    Smartphone target = request.getTargetSmartphone();
    if (target != null) {
      installOrNote(simCard, target, request, principal);
    }
  }

  /**
   * Installs the new SIM Card in its requested target Smartphone when there's still room; when the
   * Smartphone can't take it (already holds two, or is no longer Active), the SIM Card is added
   * uninstalled and {@code request}'s completion note tells the Agent why (ticket AC: "otherwise it
   * is added uninstalled and the Agent is told").
   */
  private void installOrNote(SimCard simCard, Smartphone target, Request request, AuthenticatedPrincipal principal) {
    try {
      simInstallationService.install(simCard, target, request.getId(), principal);
    } catch (ConflictException | InvalidRequestException e) {
      request.setCompletionNote(
          "The new SIM Card was added uninstalled — " + target.getModel() + " couldn't take it: " + e.getMessage());
    }
  }

  private void logProvisioned(Contract contract, Request request, SimCard simCard, AuthenticatedPrincipal principal) {
    AuditLog.simCardProvisioned(
        simCard.getId(),
        contract.getId(),
        request.getId(),
        simCard.getCarrier() == null ? null : simCard.getCarrier().getId(),
        simCard.postpaidPlanId(),
        simCard.getMonthlyFeeAmount(),
        principal.userId(),
        principal.tenantId());
  }

  private void retireReplacedSimCard(
      Contract contract, UUID requestId, UUID replacesSimCardId, AuthenticatedPrincipal principal) {
    SimCard old =
        simCardRepository
            .findByIdAndContractId(replacesSimCardId, contract.getId())
            .orElseThrow(() -> new NotFoundException("No SIM card with id " + replacesSimCardId + " on this Contract"));
    SimCardStatus oldStatus = old.getStatus();
    if (!oldStatus.canTransitionTo(SimCardStatus.RETIRED)) {
      throw new ConflictException("Cannot retire a SIM Card that is already " + oldStatus);
    }
    old.setStatus(SimCardStatus.RETIRED);
    simCardRepository.save(old);
    AuditLog.statusChanged(
        "SimCard", old.getId(), oldStatus.name(), SimCardStatus.RETIRED.name(), principal.userId(), principal.tenantId());

    // Retiring a SIM Card clears its own Installed-in link (spec.md Solution — Fleet model;
    // sim-installed-in-smartphone ticket AC).
    simInstallationService.clearLinkForRetiredSimCard(old, requestId, principal);
  }
}
