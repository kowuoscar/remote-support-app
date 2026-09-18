package com.remotesupport.backend.web.completion;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneOwner;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.web.ConflictException;
import com.remotesupport.backend.web.SimInstallationService;
import com.remotesupport.backend.web.SimInstallationService.Move;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Completing a {@link RequestType#REPLACE_SMARTPHONE} Request (replace-requests ticket AC:
 * "Completing a Replace Smartphone retires the named Smartphone, adds a company-owned one with
 * the requested or the same model, and moves the old one's SIM Cards into it; no Agent input").
 * The Smartphone to replace is always {@link Request#getTargetSmartphone()}, set at submission by
 * {@code ReplaceSmartphoneRequestDetailsHandler} — every Replace Smartphone Request has been able
 * to set it since this type was introduced, so there is no legacy Request predating it to fall
 * back for, unlike Provision Smartphone/SIM.
 */
@Component
public class ReplaceSmartphoneCompletionEffect implements RequestCompletionEffect {

  private final SmartphoneRepository smartphoneRepository;
  private final SimCardRepository simCardRepository;
  private final SimInstallationService simInstallationService;

  public ReplaceSmartphoneCompletionEffect(
      SmartphoneRepository smartphoneRepository,
      SimCardRepository simCardRepository,
      SimInstallationService simInstallationService) {
    this.smartphoneRepository = smartphoneRepository;
    this.simCardRepository = simCardRepository;
    this.simInstallationService = simInstallationService;
  }

  @Override
  public RequestType type() {
    return RequestType.REPLACE_SMARTPHONE;
  }

  @Override
  public void apply(
      Contract contract, Request request, RequestCompletionInput input, AuthenticatedPrincipal principal) {
    Smartphone old = request.getTargetSmartphone();
    if (old.getStatus() != SmartphoneStatus.ACTIVE) {
      throw new ConflictException(
          "Cannot complete this Replace Smartphone Request — the named Smartphone is no longer Active");
    }

    String model = request.getRequestedModel() != null ? request.getRequestedModel() : old.getModel();

    Smartphone replacement = new Smartphone();
    replacement.setId(UUID.randomUUID());
    replacement.setTenant(contract.getTenant());
    replacement.setContract(contract);
    replacement.setModel(model);
    replacement.setSerial(null);
    // A Smartphone reached through a Replace Request is always company-owned, exactly like
    // Provision (spec.md Solution — Fleet model).
    replacement.setOwner(SmartphoneOwner.COMPANY);
    replacement.setStatus(SmartphoneStatus.ACTIVE);
    replacement.setCreatedAt(Instant.now());
    // save()'s own return value, not `replacement` itself: this entity's id was assigned before
    // saving (every entity in this codebase is), so Spring Data merges rather than persists,
    // handing back a distinct managed instance — reusing the original, still-detached `replacement`
    // as a SIM Card's association target below would fail at flush with "object references an
    // unsaved transient instance".
    Smartphone savedReplacement = smartphoneRepository.save(replacement);

    // Carry the old Smartphone's SIM Cards onto its replacement, atomically, before retiring the
    // old one — one applyMoves call so the two-SIM check sees the Fleet as it will be once every
    // move lands, never a false positive on an intermediate state (carrier-catalog-notes.md hard
    // rule; spec.md Solution: "moves the old one's SIM Cards into it").
    List<SimCard> installed = simCardRepository.findByInstalledInSmartphoneId(old.getId());
    if (!installed.isEmpty()) {
      simInstallationService.applyMoves(
          installed.stream().map(simCard -> new Move(simCard, savedReplacement)).toList(),
          request.getId(),
          principal);
    }

    old.setStatus(SmartphoneStatus.RETIRED);
    smartphoneRepository.save(old);
    AuditLog.statusChanged(
        "Smartphone",
        old.getId(),
        SmartphoneStatus.ACTIVE.name(),
        SmartphoneStatus.RETIRED.name(),
        principal.userId(),
        principal.tenantId());

    AuditLog.smartphoneProvisioned(
        savedReplacement.getId(),
        contract.getId(),
        request.getId(),
        savedReplacement.getOwner().name(),
        principal.userId(),
        principal.tenantId());

    AuditLog.unitReplaced(
        "Smartphone",
        request.getId(),
        old.getId(),
        savedReplacement.getId(),
        principal.userId(),
        principal.tenantId());
  }
}
