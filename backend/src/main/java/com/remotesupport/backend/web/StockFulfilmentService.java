package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.PostpaidPlan;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardFlavor;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place a completion effect pulls a unit out of the Contract's own Agent's Stock onto the
 * Contract (returns-and-agent-stock spec, Solution's Fulfilment from Stock; fulfil-from-stock
 * ticket) — the reverse of {@code ReturnCompletionEffect}'s own "keep in Stock" moves, and
 * "validated in one place" per the ticket's own brief, so {@code ProvisionSmartphoneCompletionEffect},
 * {@code ProvisionSimCompletionEffect}, {@code ReplaceSmartphoneCompletionEffect} and {@code
 * ReplaceSimCompletionEffect} all call this rather than each re-deriving the ownership/matching
 * rule. "Own Stock only": scoped by {@code contract.getAgent()}, the Contract being completed's own
 * Agent — the same Agent a Manager completing on that Agent's behalf is acting for, not the
 * principal's own resolved Agent id (a Manager has none). A SIM Card additionally has to match the
 * Request's own Carrier, flavor and — for postpaid — Postpaid Plan (ticket AC); a Smartphone has no
 * matching rule beyond ownership.
 */
@Component
public class StockFulfilmentService {

  private final SmartphoneRepository smartphoneRepository;
  private final SimCardRepository simCardRepository;

  public StockFulfilmentService(SmartphoneRepository smartphoneRepository, SimCardRepository simCardRepository) {
    this.smartphoneRepository = smartphoneRepository;
    this.simCardRepository = simCardRepository;
  }

  /**
   * Takes {@code stockSmartphoneId} out of {@code contract}'s own Agent's Stock and joins it to
   * {@code contract} as Active, company-owned (ticket AC) — its status and owner are already that,
   * a Stock unit is always company-owned and Active (agent-stock ticket), so nothing else changes.
   * Refused (400) when no such Smartphone is in that Agent's Stock, which also covers "belongs to
   * another Agent" (ticket AC) since the lookup is scoped by holding Agent.
   */
  public Smartphone takeSmartphoneFromStock(
      Contract contract, UUID stockSmartphoneId, UUID requestId, AuthenticatedPrincipal principal) {
    Smartphone smartphone =
        smartphoneRepository
            .findByIdAndTenantIdAndHoldingAgentId(
                stockSmartphoneId, contract.getTenant().getId(), contract.getAgent().getId())
            .orElseThrow(
                () ->
                    new InvalidRequestException(
                        "No Smartphone with id " + stockSmartphoneId + " in this Contract's Agent's Stock"));
    smartphone.setContract(contract);
    smartphone.setHoldingAgent(null);
    smartphoneRepository.save(smartphone);

    AuditLog.unitFulfilledFromStock(
        "Smartphone",
        smartphone.getId(),
        contract.getAgent().getId(),
        contract.getId(),
        requestId,
        principal.userId(),
        principal.tenantId());
    return smartphone;
  }

  /**
   * Takes {@code stockSimCardId} out of {@code contract}'s own Agent's Stock and joins it to
   * {@code contract}, keeping its own number and monthly fee (ticket AC) — only its {@code
   * contract}/{@code holdingAgent} change. Refused (400) when no such SIM Card is in that Agent's
   * Stock (covers "another Agent's", same as the Smartphone overload), or when it doesn't match
   * {@code requiredCarrier}/{@code requiredFlavor}/{@code requiredPlan} (ticket AC: "a SIM Card
   * that doesn't match[,] is refused").
   */
  public SimCard takeMatchingSimCardFromStock(
      Contract contract,
      UUID stockSimCardId,
      Carrier requiredCarrier,
      SimCardFlavor requiredFlavor,
      PostpaidPlan requiredPlan,
      UUID requestId,
      AuthenticatedPrincipal principal) {
    SimCard simCard =
        simCardRepository
            .findByIdAndTenantIdAndHoldingAgentId(
                stockSimCardId, contract.getTenant().getId(), contract.getAgent().getId())
            .orElseThrow(
                () ->
                    new InvalidRequestException(
                        "No SIM Card with id " + stockSimCardId + " in this Contract's Agent's Stock"));
    if (!matches(simCard, requiredCarrier, requiredFlavor, requiredPlan)) {
      throw new InvalidRequestException(
          "The Stock SIM Card " + stockSimCardId + " does not match this Request's Carrier, flavor and Plan");
    }

    simCard.setContract(contract);
    simCard.setHoldingAgent(null);
    simCardRepository.save(simCard);

    AuditLog.unitFulfilledFromStock(
        "SimCard",
        simCard.getId(),
        contract.getAgent().getId(),
        contract.getId(),
        requestId,
        principal.userId(),
        principal.tenantId());
    return simCard;
  }

  private boolean matches(SimCard simCard, Carrier requiredCarrier, SimCardFlavor requiredFlavor, PostpaidPlan requiredPlan) {
    if (!Objects.equals(idOf(simCard.getCarrier()), idOf(requiredCarrier))) {
      return false;
    }
    if (simCard.getFlavor() != requiredFlavor) {
      return false;
    }
    if (requiredFlavor != SimCardFlavor.POSTPAID) {
      return true;
    }
    return Objects.equals(idOf(simCard.getPostpaidPlan()), idOf(requiredPlan));
  }

  private static UUID idOf(Carrier carrier) {
    return carrier == null ? null : carrier.getId();
  }

  private static UUID idOf(PostpaidPlan plan) {
    return plan == null ? null : plan.getId();
  }
}
