package com.remotesupport.backend.web.completion;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Disposition;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.ReturnedUnit;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.dto.SimCardCancellationRequest;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ReturnedUnitRepository;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.web.ConflictException;
import com.remotesupport.backend.web.InvalidRequestException;
import com.remotesupport.backend.web.SimInstallationService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Completing a {@link RequestType#RETURN} Request (returns-and-agent-stock spec, Solution's
 * Completion table). Every unit named on the Return was fixed at submission by {@code
 * ReturnRequestDetailsHandler} into its own {@link ReturnedUnit} row, and — for a company-owned
 * one — given its Disposition at approval ({@code RequestByIdController#approve},
 * manager-decides-return-disposition ticket). This effect applies every unit's Disposition at
 * once:
 *
 * <ul>
 *   <li>{@link Disposition#POSTED_TO_CLIENT}/{@link Disposition#POSTED_TO_COMPANY} (a Smartphone):
 *       retire it and clear its own installed SIM Cards' links (return-client-owned-smartphones
 *       ticket; both Dispositions are treated identically here — only who it goes back to differs,
 *       which is a real-world/paperwork distinction this system doesn't otherwise model).
 *   <li>{@link Disposition#CANCELLED} (a SIM Card): the Agent's own {@code
 *       RequestCompletionInput#simCardCancellations} must carry an effective date for it — refused
 *       (400) without one; retire it, keep the date, and uninstall it if it was installed anywhere.
 *   <li>{@link Disposition#KEPT_IN_STOCK} (either kind, agent-stock ticket): the unit leaves the
 *       Contract and joins the Stock of the Contract's own Agent (spec.md Solution's Agent Stock:
 *       "units enter through a Return") — {@code contract} is cleared and {@code holdingAgent} set
 *       to {@code request.getContract().getAgent()}, uninstalled the same way a retired unit is.
 *       Status is left {@code ACTIVE}: a Stock unit is still a usable unit, just off every Fleet.
 * </ul>
 *
 * <p>Needs no Agent input at all when nothing is being cancelled (unlike every Fleet-changing
 * completion effect but Replace Smartphone's): {@link RequestCompletionInput} is only read for its
 * {@code simCardCancellations}.
 */
@Component
public class ReturnCompletionEffect implements RequestCompletionEffect {

  private final ReturnedUnitRepository returnedUnitRepository;
  private final SmartphoneRepository smartphoneRepository;
  private final SimCardRepository simCardRepository;
  private final SimInstallationService simInstallationService;

  public ReturnCompletionEffect(
      ReturnedUnitRepository returnedUnitRepository,
      SmartphoneRepository smartphoneRepository,
      SimCardRepository simCardRepository,
      SimInstallationService simInstallationService) {
    this.returnedUnitRepository = returnedUnitRepository;
    this.smartphoneRepository = smartphoneRepository;
    this.simCardRepository = simCardRepository;
    this.simInstallationService = simInstallationService;
  }

  @Override
  public RequestType type() {
    return RequestType.RETURN;
  }

  @Override
  public void apply(
      Contract contract, Request request, RequestCompletionInput input, AuthenticatedPrincipal principal) {
    List<ReturnedUnit> units = returnedUnitRepository.forRequest(request);
    Map<UUID, LocalDate> cancellationDates = indexCancellationDates(input.simCardCancellations());

    // Every named unit must still be Active, and every SIM Card being Cancelled must have its
    // date given, before anything is written (ticket ACs: "refused with a clear message if a named
    // unit is no longer Active" / "refused without one") — checked for all units first, so a later
    // one failing never leaves an earlier one already retired.
    for (ReturnedUnit unit : units) {
      Smartphone smartphone = unit.getSmartphone();
      if (smartphone != null && smartphone.getStatus() != SmartphoneStatus.ACTIVE) {
        throw new ConflictException(
            "Cannot complete this Return — the Smartphone " + smartphone.getId() + " is no longer Active");
      }
      SimCard simCard = unit.getSimCard();
      if (simCard != null) {
        if (simCard.getStatus() != SimCardStatus.ACTIVE) {
          throw new ConflictException(
              "Cannot complete this Return — the SIM Card " + simCard.getId() + " is no longer Active");
        }
        if (unit.getDisposition() == Disposition.CANCELLED && !cancellationDates.containsKey(simCard.getId())) {
          throw new InvalidRequestException(
              "An effective cancellation date is required for the cancelled SIM Card " + simCard.getId());
        }
      }
    }

    for (ReturnedUnit unit : units) {
      Smartphone smartphone = unit.getSmartphone();
      if (smartphone != null) {
        if (unit.getDisposition() == Disposition.KEPT_IN_STOCK) {
          keepSmartphoneInStock(request, contract, smartphone, unit, principal);
        } else {
          retireSmartphone(request, smartphone, unit, principal);
        }
        continue;
      }
      SimCard simCard = unit.getSimCard();
      if (simCard != null) {
        if (unit.getDisposition() == Disposition.CANCELLED) {
          cancelSimCard(request, simCard, unit, cancellationDates.get(simCard.getId()), principal);
        } else if (unit.getDisposition() == Disposition.KEPT_IN_STOCK) {
          keepSimCardInStock(request, contract, simCard, unit, principal);
        }
      }
    }
  }

  private static Map<UUID, LocalDate> indexCancellationDates(List<SimCardCancellationRequest> cancellations) {
    Map<UUID, LocalDate> dates = new HashMap<>();
    if (cancellations == null) {
      return dates;
    }
    for (SimCardCancellationRequest cancellation : cancellations) {
      dates.put(cancellation.simCardId(), cancellation.effectiveDate());
    }
    return dates;
  }

  /**
   * Posted to Client or Posted to company: retires the Smartphone and uninstalls its SIM Cards,
   * which stay in the Fleet (return-client-owned-smartphones ticket AC) — mirrors {@code
   * SmartphoneController#updateStatus}'s own retire-then-clear-links order. Which Disposition it
   * was only changes the audit entry.
   */
  private void retireSmartphone(Request request, Smartphone smartphone, ReturnedUnit unit, AuthenticatedPrincipal principal) {
    smartphone.setStatus(SmartphoneStatus.RETIRED);
    smartphoneRepository.save(smartphone);
    AuditLog.statusChanged(
        "Smartphone",
        smartphone.getId(),
        SmartphoneStatus.ACTIVE.name(),
        SmartphoneStatus.RETIRED.name(),
        principal.userId(),
        principal.tenantId());

    simInstallationService.clearLinksForRetiredSmartphone(smartphone, request.getId(), principal);

    AuditLog.unitReturned(
        "Smartphone",
        smartphone.getId(),
        request.getId(),
        unit.getDisposition().name(),
        principal.userId(),
        principal.tenantId());
  }

  /**
   * Cancelled: retires the SIM Card, keeps its effective cancellation date, and uninstalls it if
   * it was installed anywhere (manager-decides-return-disposition ticket AC: "a cancelled SIM Card
   * is retired, uninstalled, and keeps its cancellation date").
   */
  private void cancelSimCard(
      Request request, SimCard simCard, ReturnedUnit unit, LocalDate effectiveDate, AuthenticatedPrincipal principal) {
    simCard.setStatus(SimCardStatus.RETIRED);
    simCard.setCancellationEffectiveDate(effectiveDate);
    simCardRepository.save(simCard);
    AuditLog.statusChanged(
        "SimCard", simCard.getId(), SimCardStatus.ACTIVE.name(), SimCardStatus.RETIRED.name(),
        principal.userId(), principal.tenantId());

    simInstallationService.clearLinkForRetiredSimCard(simCard, request.getId(), principal);

    AuditLog.unitReturned(
        "SimCard", simCard.getId(), request.getId(), unit.getDisposition().name(), principal.userId(), principal.tenantId());
    AuditLog.simCardCancelled(simCard.getId(), effectiveDate, request.getId(), principal.userId(), principal.tenantId());
  }

  /**
   * Kept in Stock (agent-stock ticket): the Smartphone leaves {@code contract} and joins {@code
   * contract.getAgent()}'s Stock instead — its own installed SIM Cards are uninstalled (stay in the
   * Fleet) the same way a retired Smartphone's are, since a Stock unit shows on no Fleet at all
   * (spec.md Solution's Agent Stock). Status is left {@code ACTIVE}.
   */
  private void keepSmartphoneInStock(
      Request request, Contract contract, Smartphone smartphone, ReturnedUnit unit, AuthenticatedPrincipal principal) {
    Agent agent = contract.getAgent();
    UUID fromContractId = contract.getId();

    simInstallationService.clearLinksForRetiredSmartphone(smartphone, request.getId(), principal);

    smartphone.setContract(null);
    smartphone.setHoldingAgent(agent);
    smartphoneRepository.save(smartphone);

    AuditLog.unitMovedToStock(
        "Smartphone", smartphone.getId(), fromContractId, agent.getId(), request.getId(), principal.userId(), principal.tenantId());
    AuditLog.unitReturned(
        "Smartphone", smartphone.getId(), request.getId(), unit.getDisposition().name(), principal.userId(), principal.tenantId());
  }

  /**
   * Kept in Stock (agent-stock ticket): the SIM Card leaves {@code contract} and joins {@code
   * contract.getAgent()}'s Stock instead, uninstalled from wherever it sat. Status is left {@code
   * ACTIVE} — a Stock SIM Card is still usable, just off every Fleet (spec.md Solution's Agent
   * Stock).
   */
  private void keepSimCardInStock(
      Request request, Contract contract, SimCard simCard, ReturnedUnit unit, AuthenticatedPrincipal principal) {
    Agent agent = contract.getAgent();
    UUID fromContractId = contract.getId();

    simInstallationService.clearLinkForRetiredSimCard(simCard, request.getId(), principal);

    simCard.setContract(null);
    simCard.setHoldingAgent(agent);
    simCardRepository.save(simCard);

    AuditLog.unitMovedToStock(
        "SimCard", simCard.getId(), fromContractId, agent.getId(), request.getId(), principal.userId(), principal.tenantId());
    AuditLog.unitReturned(
        "SimCard", simCard.getId(), request.getId(), unit.getDisposition().name(), principal.userId(), principal.tenantId());
  }
}
