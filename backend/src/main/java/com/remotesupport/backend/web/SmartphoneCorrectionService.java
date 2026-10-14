package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
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

  private final SmartphoneRepository smartphoneRepository;

  public SmartphoneCorrectionService(SmartphoneRepository smartphoneRepository) {
    this.smartphoneRepository = smartphoneRepository;
  }

  /** Trims, validates and stores the model; the model already held saves nothing and logs nothing. */
  @Transactional
  public Smartphone correctModel(
      Contract contract, UUID smartphoneId, String requestedModel, AuthenticatedPrincipal principal) {
    Smartphone smartphone =
        smartphoneRepository
            .findByIdAndContractId(smartphoneId, contract.getId())
            .orElseThrow(() -> new NotFoundException("No smartphone with id " + smartphoneId));

    String model = requestedModel == null ? "" : requestedModel.trim();
    if (model.isEmpty()) {
      throw new InvalidCorrectionException("MODEL_BLANK", "Enter a model.");
    }
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
