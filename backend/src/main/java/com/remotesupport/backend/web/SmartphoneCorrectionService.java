package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.SmartphoneOwner;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ReturnedUnitRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corrections of a Smartphone on a Contract's Fleet (correct-a-fleet-unit spec). The caller has
 * already passed {@link com.remotesupport.backend.security.FleetAccessGuard#requireCanCorrect}.
 */
@Service
public class SmartphoneCorrectionService {

  static final int MODEL_MAX_LENGTH = 255;

  private static final List<RequestStatus> OPEN_RETURN_STATUSES =
      List.of(RequestStatus.PENDING_APPROVAL, RequestStatus.SUBMITTED, RequestStatus.IN_PROGRESS);

  private final SmartphoneRepository smartphoneRepository;
  private final ReturnedUnitRepository returnedUnitRepository;

  public SmartphoneCorrectionService(
      SmartphoneRepository smartphoneRepository, ReturnedUnitRepository returnedUnitRepository) {
    this.smartphoneRepository = smartphoneRepository;
    this.returnedUnitRepository = returnedUnitRepository;
  }

  /**
   * Stores the owner. Refused while an open Return names the phone (its approval and Disposition
   * were fixed from the owner at submission); the owner already held saves nothing and logs nothing.
   */
  @Transactional
  public Smartphone correctOwner(
      Contract contract,
      UUID smartphoneId,
      SmartphoneOwner requestedOwner,
      AuthenticatedPrincipal principal) {
    Smartphone smartphone =
        smartphoneRepository
            .findByIdAndContractId(smartphoneId, contract.getId())
            .orElseThrow(() -> new NotFoundException("No smartphone with id " + smartphoneId));

    SmartphoneOwner oldOwner = smartphone.getOwner();
    if (requestedOwner == oldOwner) {
      return smartphone;
    }
    if (returnedUnitRepository.existsBySmartphoneIdAndRequestStatusIn(
        smartphoneId, OPEN_RETURN_STATUSES)) {
      throw new CorrectionConflictException(
          "SMARTPHONE_ON_OPEN_RETURN",
          "This smartphone is on an open Return. Change its owner once the Return is done.");
    }
    smartphone.setOwner(requestedOwner);
    smartphoneRepository.save(smartphone);

    AuditLog.smartphoneOwnerCorrected(
        smartphone.getId(),
        contract.getId(),
        oldOwner.name(),
        requestedOwner.name(),
        principal.userId(),
        principal.role(),
        principal.tenantId());
    return smartphone;
  }

  /** Trims, length-checks and stores the model (presence is the controller's); the model already held saves nothing and logs nothing. */
  @Transactional
  public Smartphone correctModel(
      Contract contract, UUID smartphoneId, String requestedModel, AuthenticatedPrincipal principal) {
    Smartphone smartphone =
        smartphoneRepository
            .findByIdAndContractId(smartphoneId, contract.getId())
            .orElseThrow(() -> new NotFoundException("No smartphone with id " + smartphoneId));

    String model = requestedModel.trim();
    if (model.length() > MODEL_MAX_LENGTH) {
      throw new InvalidCorrectionException(
          "MODEL_TOO_LONG", "A model can be at most " + MODEL_MAX_LENGTH + " characters.");
    }

    String oldModel = smartphone.getModel();
    if (model.equals(oldModel)) {
      return smartphone;
    }
    smartphone.setModel(model);
    smartphoneRepository.save(smartphone);

    AuditLog.smartphoneModelCorrected(
        smartphone.getId(),
        contract.getId(),
        oldModel,
        model,
        principal.userId(),
        principal.role(),
        principal.tenantId());
    return smartphone;
  }
}
