package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corrections of a SIM Card on a Contract's Fleet (correct-a-fleet-unit spec). The caller has
 * already passed {@link com.remotesupport.backend.security.FleetAccessGuard#requireCanCorrect}.
 * A number is not checked for duplicates (spec, Decisions taken; recorded in docs/tech-debt.md).
 */
@Service
public class SimCardCorrectionService {

  static final int NUMBER_MAX_LENGTH = 64;

  private final SimCardRepository simCardRepository;

  public SimCardCorrectionService(SimCardRepository simCardRepository) {
    this.simCardRepository = simCardRepository;
  }

  /** Trims, validates and stores the number; the number already held saves nothing and logs nothing. */
  @Transactional
  public SimCard correctNumber(
      Contract contract, UUID simCardId, String requestedNumber, AuthenticatedPrincipal principal) {
    SimCard simCard =
        simCardRepository
            .findByIdAndContractId(simCardId, contract.getId())
            .orElseThrow(() -> new NotFoundException("No SIM card with id " + simCardId));

    String number = requestedNumber == null ? "" : requestedNumber.trim();
    if (number.isEmpty()) {
      throw new InvalidCorrectionException("NUMBER_BLANK", "Enter a number.");
    }
    if (number.length() > NUMBER_MAX_LENGTH) {
      throw new InvalidCorrectionException(
          "NUMBER_TOO_LONG", "A number can be at most " + NUMBER_MAX_LENGTH + " characters.");
    }

    String oldNumber = simCard.getNumber();
    if (number.equals(oldNumber)) {
      return simCard;
    }
    simCard.setNumber(number);
    simCardRepository.save(simCard);

    AuditLog.simCardNumberCorrected(
        simCard.getId(),
        contract.getId(),
        oldNumber,
        number,
        principal.userId(),
        principal.role(),
        principal.tenantId());
    return simCard;
  }
}
