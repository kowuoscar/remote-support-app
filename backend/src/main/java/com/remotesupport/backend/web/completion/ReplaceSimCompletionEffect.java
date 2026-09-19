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
import com.remotesupport.backend.web.SimCardFactory;
import com.remotesupport.backend.web.SimInstallationService;
import com.remotesupport.backend.web.SimInstallationService.Move;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Completing a {@link RequestType#REPLACE_SIM} Request (replace-requests ticket AC: "Completing a
 * Replace SIM asks for the new SIM Card's details, defaulted from the old one's; the old SIM Card
 * is retired and the new one is installed where the old one was"). The SIM Card to replace is
 * always {@link Request#getTargetSimCard()}, set at submission by {@code
 * ReplaceSimRequestDetailsHandler}. Unlike Provision SIM, there is no "requested" Carrier/flavor/
 * Plan stored on the Request to complete from — the new SIM Card's own details always come from
 * {@link RequestCompletionInput#newSimCard()} (the Agent's own input; the frontend pre-fills the
 * form's defaults from the old SIM Card's current values, but that's a UI concern, not a
 * server-side one) and go through {@link SimCardFactory#create}, the same validation every other
 * SIM-creation path uses.
 */
@Component
public class ReplaceSimCompletionEffect implements RequestCompletionEffect {

  private final SimCardRepository simCardRepository;
  private final SimCardFactory simCardFactory;
  private final SimInstallationService simInstallationService;

  public ReplaceSimCompletionEffect(
      SimCardRepository simCardRepository, SimCardFactory simCardFactory, SimInstallationService simInstallationService) {
    this.simCardRepository = simCardRepository;
    this.simCardFactory = simCardFactory;
    this.simInstallationService = simInstallationService;
  }

  @Override
  public RequestType type() {
    return RequestType.REPLACE_SIM;
  }

  @Override
  public void apply(
      Contract contract, Request request, RequestCompletionInput input, AuthenticatedPrincipal principal) {
    SimCard old = request.getTargetSimCard();
    if (old.getStatus() != SimCardStatus.ACTIVE) {
      throw new ConflictException(
          "Cannot complete this Replace SIM Request — the named SIM Card is no longer Active");
    }

    SimCardCreateRequest newSimCard = input.newSimCard();
    if (newSimCard == null) {
      throw new InvalidRequestException("newSimCard details are required to complete a Replace SIM request");
    }

    // Captured before either SIM Card's own Installed-in link changes below.
    Smartphone oldSmartphone = old.getInstalledInSmartphone();

    SimCard replacement = simCardFactory.create(contract, newSimCard);

    old.setStatus(SimCardStatus.RETIRED);
    simCardRepository.save(old);
    AuditLog.statusChanged(
        "SimCard", old.getId(), SimCardStatus.ACTIVE.name(), SimCardStatus.RETIRED.name(), principal.userId(), principal.tenantId());

    // The new SIM Card takes over the old one's slot in the same atomic move that vacates it, so
    // the two-SIM check never sees a false positive on an intermediate state (spec.md Solution:
    // "the new one takes its Smartphone"; carrier-catalog-notes.md hard rule: applyMoves with 2
    // moves at once for a Replace's SIM carry-over/slot take-over). A no-op pair when the old SIM
    // Card wasn't installed anywhere.
    simInstallationService.applyMoves(
        List.of(new Move(old, null), new Move(replacement, oldSmartphone)), request.getId(), principal);

    AuditLog.simCardProvisioned(
        replacement.getId(),
        contract.getId(),
        request.getId(),
        replacement.getCarrier() == null ? null : replacement.getCarrier().getId(),
        replacement.postpaidPlanId(),
        replacement.getMonthlyFeeAmount(),
        principal.userId(),
        principal.tenantId());

    AuditLog.unitReplaced(
        "SimCard", request.getId(), old.getId(), replacement.getId(), principal.userId(), principal.tenantId());
  }
}
