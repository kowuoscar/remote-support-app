package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.PostpaidPlan;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardFlavor;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.math.BigDecimal;
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
  private final SimCardFactory simCardFactory;

  public SimCardCorrectionService(SimCardRepository simCardRepository, SimCardFactory simCardFactory) {
    this.simCardRepository = simCardRepository;
    this.simCardFactory = simCardFactory;
  }

  /**
   * Puts a Postpaid SIM on another plan of its own Carrier and re-copies the plan's price into its
   * monthly fee, in this one transaction. The plan already held saves nothing and logs nothing.
   * The plan rule is {@link SimCardFactory#requireUsablePlan}; the Carrier is only checked for
   * being present and not archived, since it is not being chosen again.
   */
  @Transactional
  public SimCard correctPostpaidPlan(
      Contract contract, UUID simCardId, UUID requestedPlanId, AuthenticatedPrincipal principal) {
    SimCard simCard =
        simCardRepository
            .findByIdAndContractId(simCardId, contract.getId())
            .orElseThrow(() -> new NotFoundException("No SIM card with id " + simCardId));

    if (simCard.getFlavor() == SimCardFlavor.PREPAID) {
      throw new InvalidCorrectionException("PREPAID_HAS_NO_PLAN", "A Prepaid SIM has no Postpaid plan.");
    }
    Carrier carrier = simCard.getCarrier();
    if (carrier == null || carrier.isArchived()) {
      throw new InvalidCorrectionException(
          "CARRIER_NOT_USABLE", "This SIM's Carrier is missing or archived, so it cannot take a plan.");
    }
    PostpaidPlan plan;
    try {
      plan = simCardFactory.requireUsablePlan(carrier, SimCardFlavor.POSTPAID, requestedPlanId);
    } catch (UnusablePlanException e) {
      throw new InvalidCorrectionException(e.getCode(), e.getMessage());
    }

    UUID oldPlanId = simCard.postpaidPlanId();
    BigDecimal oldFee = simCard.getMonthlyFeeAmount();
    if (plan.getId().equals(oldPlanId)) {
      return simCard;
    }
    simCard.setPostpaidPlan(plan);
    simCard.setMonthlyFeeAmount(plan.getPrice());
    simCardRepository.save(simCard);

    AuditLog.simCardPostpaidPlanCorrected(
        simCard.getId(),
        contract.getId(),
        oldPlanId,
        plan.getId(),
        oldFee,
        plan.getPrice(),
        principal.userId(),
        principal.role(),
        principal.tenantId());
    return simCard;
  }

  /** Trims, length-checks and stores the number (presence is the controller's); the number already held saves nothing and logs nothing. */
  @Transactional
  public SimCard correctNumber(
      Contract contract, UUID simCardId, String requestedNumber, AuthenticatedPrincipal principal) {
    SimCard simCard =
        simCardRepository
            .findByIdAndContractId(simCardId, contract.getId())
            .orElseThrow(() -> new NotFoundException("No SIM card with id " + simCardId));

    String number = requestedNumber.trim();
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
