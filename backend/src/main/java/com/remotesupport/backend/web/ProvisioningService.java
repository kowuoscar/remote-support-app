package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneOwner;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.dto.SimCardCreateRequest;
import com.remotesupport.backend.dto.SmartphoneCreateRequest;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The provisioning side-effect of completing a {@code PROVISION_SMARTPHONE}/{@code PROVISION_SIM}
 * Request (fee-logging-and-provisioning ticket AC: "Completing a Provision Smartphone or
 * Provision SIM Request adds the new unit to the Contract's Fleet, retiring the unit it replaces
 * where one is specified"). Shared by every path a such a Request can reach {@code COMPLETED}
 * through: {@link RequestController#updateStatus} (Tester-raised, later completed by the Agent),
 * {@link RequestController#create} when an Agent logs one proactively starting immediately {@code
 * COMPLETED}, and {@link FeeController} when a proactive Fee's own auto-created linking Request is
 * a provisioning type. Pulled out of all three controllers rather than duplicated, since the
 * validation and Fleet-write logic is identical regardless of which path reached {@code
 * COMPLETED} — only {@link SmartphoneController}/{@link SimCardController}'s Manager-driven
 * creation stays separate, since that path never completes a Request.
 */
@Component
public class ProvisioningService {

  private final SmartphoneRepository smartphoneRepository;
  private final SimCardRepository simCardRepository;
  private final SimCardFactory simCardFactory;

  public ProvisioningService(
      SmartphoneRepository smartphoneRepository,
      SimCardRepository simCardRepository,
      SimCardFactory simCardFactory) {
    this.smartphoneRepository = smartphoneRepository;
    this.simCardRepository = simCardRepository;
    this.simCardFactory = simCardFactory;
  }

  /**
   * Applies the Fleet side-effect if {@code request} is a provisioning type and has just reached
   * {@code COMPLETED}; a no-op for every other type/status combination (e.g. a Topup or Repair
   * being completed, or a provisioning Request moving to a non-terminal status).
   */
  public void applyIfNeeded(
      Contract contract,
      Request request,
      SmartphoneCreateRequest newSmartphone,
      SimCardCreateRequest newSimCard,
      UUID replacesSmartphoneId,
      UUID replacesSimCardId,
      AuthenticatedPrincipal principal) {
    if (request.getStatus() != RequestStatus.COMPLETED) {
      return;
    }
    if (request.getType() == RequestType.PROVISION_SMARTPHONE) {
      provisionSmartphone(contract, request, newSmartphone, replacesSmartphoneId, principal);
    } else if (request.getType() == RequestType.PROVISION_SIM) {
      provisionSimCard(contract, request, newSimCard, replacesSimCardId, principal);
    }
  }

  private void provisionSmartphone(
      Contract contract,
      Request request,
      SmartphoneCreateRequest newSmartphone,
      UUID replacesSmartphoneId,
      AuthenticatedPrincipal principal) {
    if (newSmartphone == null) {
      throw new InvalidRequestException(
          "newSmartphone details are required to complete a Provision Smartphone request");
    }

    Smartphone smartphone = new Smartphone();
    smartphone.setId(UUID.randomUUID());
    smartphone.setTenant(contract.getTenant());
    smartphone.setContract(contract);
    smartphone.setModel(newSmartphone.model());
    smartphone.setSerial(StringUtils.hasText(newSmartphone.serial()) ? newSmartphone.serial() : null);
    // A Smartphone reached through a Provision Request is always company-owned (spec.md Fleet
    // model; smartphone-owner-and-optional-serial ticket AC), regardless of anything
    // newSmartphone.owner() might carry.
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

    request.setReplacesSmartphoneId(replacesSmartphoneId);
    if (replacesSmartphoneId != null) {
      Smartphone old =
          smartphoneRepository
              .findByIdAndContractId(replacesSmartphoneId, contract.getId())
              .orElseThrow(
                  () ->
                      new NotFoundException(
                          "No smartphone with id " + replacesSmartphoneId + " on this Contract"));
      retireSmartphone(old, principal);
    }
  }

  private void retireSmartphone(Smartphone smartphone, AuthenticatedPrincipal principal) {
    SmartphoneStatus oldStatus = smartphone.getStatus();
    if (!oldStatus.canTransitionTo(SmartphoneStatus.RETIRED)) {
      throw new ConflictException("Cannot retire a Smartphone that is already " + oldStatus);
    }
    smartphone.setStatus(SmartphoneStatus.RETIRED);
    smartphoneRepository.save(smartphone);
    AuditLog.statusChanged(
        "Smartphone",
        smartphone.getId(),
        oldStatus.name(),
        SmartphoneStatus.RETIRED.name(),
        principal.userId(),
        principal.tenantId());
  }

  private void provisionSimCard(
      Contract contract,
      Request request,
      SimCardCreateRequest newSimCard,
      UUID replacesSimCardId,
      AuthenticatedPrincipal principal) {
    if (newSimCard == null) {
      throw new InvalidRequestException(
          "newSimCard details are required to complete a Provision SIM request");
    }
    SimCard simCard = simCardFactory.create(contract, newSimCard);

    AuditLog.simCardProvisioned(
        simCard.getId(),
        contract.getId(),
        request.getId(),
        simCard.getCarrier().getId(),
        simCard.postpaidPlanId(),
        simCard.getMonthlyFeeAmount(),
        principal.userId(),
        principal.tenantId());

    request.setReplacesSimCardId(replacesSimCardId);
    if (replacesSimCardId != null) {
      SimCard old =
          simCardRepository
              .findByIdAndContractId(replacesSimCardId, contract.getId())
              .orElseThrow(
                  () ->
                      new NotFoundException(
                          "No SIM card with id " + replacesSimCardId + " on this Contract"));
      retireSimCard(old, principal);
    }
  }

  private void retireSimCard(SimCard simCard, AuthenticatedPrincipal principal) {
    SimCardStatus oldStatus = simCard.getStatus();
    if (!oldStatus.canTransitionTo(SimCardStatus.RETIRED)) {
      throw new ConflictException("Cannot retire a SIM Card that is already " + oldStatus);
    }
    simCard.setStatus(SimCardStatus.RETIRED);
    simCardRepository.save(simCard);
    AuditLog.statusChanged(
        "SimCard",
        simCard.getId(),
        oldStatus.name(),
        SimCardStatus.RETIRED.name(),
        principal.userId(),
        principal.tenantId());
  }
}
