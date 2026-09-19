package com.remotesupport.backend.web.completion;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.ReturnedUnit;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ReturnedUnitRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.web.ConflictException;
import com.remotesupport.backend.web.SimInstallationService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Completing a {@link RequestType#RETURN} Request (returns-and-agent-stock spec, Solution's
 * Completion table; return-client-owned-smartphones ticket AC: "Completing the Return retires each
 * Smartphone and uninstalls its SIM Cards, which stay in the Fleet"). Every unit named on the
 * Return was fixed at submission by {@code ReturnRequestDetailsHandler} into its own {@link
 * ReturnedUnit} row; this ticket only ever produces {@code POSTED_TO_CLIENT} rows naming a
 * Smartphone (a SIM Card, or a company-owned Smartphone, is refused before a Return can even be
 * submitted), so this effect only knows how to retire a Smartphone so far — {@code
 * manager-decides-return-disposition} and {@code agent-stock} add the Cancelled/Kept-in-Stock
 * branches, for both unit kinds.
 *
 * <p>Needs no Agent input at all (unlike every Fleet-changing completion effect but Replace
 * Smartphone's): {@link RequestCompletionInput} is accepted only to satisfy {@link
 * RequestCompletionEffect}'s shared interface, exactly like {@code ReplaceSmartphoneCompletionEffect}.
 */
@Component
public class ReturnCompletionEffect implements RequestCompletionEffect {

  private final ReturnedUnitRepository returnedUnitRepository;
  private final SmartphoneRepository smartphoneRepository;
  private final SimInstallationService simInstallationService;

  public ReturnCompletionEffect(
      ReturnedUnitRepository returnedUnitRepository,
      SmartphoneRepository smartphoneRepository,
      SimInstallationService simInstallationService) {
    this.returnedUnitRepository = returnedUnitRepository;
    this.smartphoneRepository = smartphoneRepository;
    this.simInstallationService = simInstallationService;
  }

  @Override
  public RequestType type() {
    return RequestType.RETURN;
  }

  @Override
  public void apply(
      Contract contract, Request request, RequestCompletionInput input, AuthenticatedPrincipal principal) {
    List<ReturnedUnit> units = returnedUnitRepository.findByRequestIdOrderByCreatedAtAsc(request.getId());

    // Every named unit must still be Active before anything is written (ticket AC: "Completion is
    // refused with a clear message if a named unit is no longer Active") — checked for all units
    // first, so a later one failing never leaves an earlier one already retired.
    for (ReturnedUnit unit : units) {
      Smartphone smartphone = unit.getSmartphone();
      if (smartphone != null && smartphone.getStatus() != SmartphoneStatus.ACTIVE) {
        throw new ConflictException(
            "Cannot complete this Return — the Smartphone " + smartphone.getId() + " is no longer Active");
      }
    }

    for (ReturnedUnit unit : units) {
      Smartphone smartphone = unit.getSmartphone();
      if (smartphone != null) {
        retire(request, smartphone, unit, principal);
      }
    }
  }

  /**
   * Posted to Client (the only Disposition this ticket ever writes): retires the Smartphone and
   * uninstalls its SIM Cards, which stay in the Fleet (ticket AC) — mirrors {@code
   * SmartphoneController#updateStatus}'s own retire-then-clear-links order.
   */
  private void retire(Request request, Smartphone smartphone, ReturnedUnit unit, AuthenticatedPrincipal principal) {
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
}
