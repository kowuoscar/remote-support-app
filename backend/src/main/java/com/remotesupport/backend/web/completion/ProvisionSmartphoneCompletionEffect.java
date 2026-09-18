package com.remotesupport.backend.web.completion;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneOwner;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.dto.SmartphoneCreateRequest;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.web.ConflictException;
import com.remotesupport.backend.web.InvalidRequestException;
import com.remotesupport.backend.web.NotFoundException;
import com.remotesupport.backend.web.SimInstallationService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Completing a {@link RequestType#PROVISION_SMARTPHONE} Request (provision-request-details ticket
 * AC: "Completing a Provision Smartphone needs no Agent input and adds a company-owned Smartphone
 * with the requested model and no serial"). A Request that already carries its own {@code
 * requestedModel} from submission (every one submitted since this ticket) takes this new,
 * no-input path; one from before this ticket ({@code requestedModel} null) falls back to the
 * previous full form — the same {@code newSmartphone}/{@code replacesSmartphoneId} shape
 * fee-logging-and-provisioning originally shipped (ticket AC: "completes through the previous
 * full form").
 */
@Component
public class ProvisionSmartphoneCompletionEffect implements RequestCompletionEffect {

  private final SmartphoneRepository smartphoneRepository;
  private final SimInstallationService simInstallationService;

  public ProvisionSmartphoneCompletionEffect(
      SmartphoneRepository smartphoneRepository, SimInstallationService simInstallationService) {
    this.smartphoneRepository = smartphoneRepository;
    this.simInstallationService = simInstallationService;
  }

  @Override
  public RequestType type() {
    return RequestType.PROVISION_SMARTPHONE;
  }

  @Override
  public void apply(Contract contract, Request request, RequestCompletionInput input, AuthenticatedPrincipal principal) {
    if (request.getRequestedModel() != null) {
      addProvisionedSmartphone(contract, request, request.getRequestedModel(), null, principal);
      return;
    }

    // Legacy fallback: a Request submitted before this ticket carries no requestedModel, so it
    // completes exactly the way it always did — the full form, optionally retiring a named unit.
    SmartphoneCreateRequest newSmartphone = input.newSmartphone();
    if (newSmartphone == null) {
      throw new InvalidRequestException(
          "newSmartphone details are required to complete a Provision Smartphone request");
    }
    addProvisionedSmartphone(contract, request, newSmartphone.model(), newSmartphone.serial(), principal);

    request.setReplacesSmartphoneId(input.replacesSmartphoneId());
    if (input.replacesSmartphoneId() != null) {
      retireReplacedSmartphone(contract, request.getId(), input.replacesSmartphoneId(), principal);
    }
  }

  private void addProvisionedSmartphone(
      Contract contract, Request request, String model, String serial, AuthenticatedPrincipal principal) {
    Smartphone smartphone = new Smartphone();
    smartphone.setId(UUID.randomUUID());
    smartphone.setTenant(contract.getTenant());
    smartphone.setContract(contract);
    smartphone.setModel(model);
    smartphone.setSerial(StringUtils.hasText(serial) ? serial : null);
    // A Smartphone reached through a Provision Request is always company-owned (spec.md Fleet
    // model; smartphone-owner-and-optional-serial ticket AC).
    smartphone.setOwner(SmartphoneOwner.COMPANY);
    smartphone.setStatus(SmartphoneStatus.ACTIVE);
    smartphone.setCreatedAt(Instant.now());
    smartphoneRepository.save(smartphone);

    AuditLog.smartphoneProvisioned(
        smartphone.getId(),
        contract.getId(),
        request.getId(),
        smartphone.getOwner().name(),
        principal.userId(),
        principal.tenantId());
  }

  private void retireReplacedSmartphone(
      Contract contract, UUID requestId, UUID replacesSmartphoneId, AuthenticatedPrincipal principal) {
    Smartphone old =
        smartphoneRepository
            .findByIdAndContractId(replacesSmartphoneId, contract.getId())
            .orElseThrow(
                () -> new NotFoundException("No smartphone with id " + replacesSmartphoneId + " on this Contract"));
    SmartphoneStatus oldStatus = old.getStatus();
    if (!oldStatus.canTransitionTo(SmartphoneStatus.RETIRED)) {
      throw new ConflictException("Cannot retire a Smartphone that is already " + oldStatus);
    }
    old.setStatus(SmartphoneStatus.RETIRED);
    smartphoneRepository.save(old);
    AuditLog.statusChanged(
        "Smartphone", old.getId(), oldStatus.name(), SmartphoneStatus.RETIRED.name(), principal.userId(), principal.tenantId());

    // Retiring a Smartphone clears the Installed-in link on its SIM Cards (spec.md Solution —
    // Fleet model; sim-installed-in-smartphone ticket AC).
    simInstallationService.clearLinksForRetiredSmartphone(old, requestId, principal);
  }
}
