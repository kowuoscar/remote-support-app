package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.PostpaidPlan;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardFlavor;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.dto.SimCardCreateRequest;
import com.remotesupport.backend.repository.CarrierRepository;
import com.remotesupport.backend.repository.PostpaidPlanRepository;
import com.remotesupport.backend.repository.SimCardRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place a SIM Card comes into a Fleet, shared by every SIM-creation path (carrier-catalog
 * spec, SIM Card changes): the Manager's {@link SimCardController}, and {@link ProvisioningService}
 * for completing a Provision SIM Request and for an Agent's proactive Request or Fee. Each path
 * keeps its own authorization and audit event; what makes a SIM Card valid lives here, so no path
 * can skip a rule.
 */
@Component
public class SimCardFactory {

  private final SimCardRepository simCardRepository;
  private final CarrierRepository carrierRepository;
  private final PostpaidPlanRepository postpaidPlanRepository;

  public SimCardFactory(
      SimCardRepository simCardRepository,
      CarrierRepository carrierRepository,
      PostpaidPlanRepository postpaidPlanRepository) {
    this.simCardRepository = simCardRepository;
    this.carrierRepository = carrierRepository;
    this.postpaidPlanRepository = postpaidPlanRepository;
  }

  /** Validates the request against {@code contract} and saves a new, Active SIM Card on its Fleet. */
  public SimCard create(Contract contract, SimCardCreateRequest request) {
    Carrier carrier = requireUsableCarrier(contract, request.carrierId());

    PostpaidPlan plan = requireUsablePlan(carrier, request.flavor(), request.postpaidPlanId());

    SimCard simCard = new SimCard();
    simCard.setId(UUID.randomUUID());
    simCard.setTenant(contract.getTenant());
    simCard.setContract(contract);
    simCard.setNumber(request.number());
    simCard.setCarrier(carrier);
    simCard.setFlavor(request.flavor());
    simCard.setPostpaidPlan(plan);
    simCard.setMonthlyFeeAmount(plan == null ? null : plan.getPrice());
    simCard.setStatus(SimCardStatus.ACTIVE);
    simCard.setCreatedAt(Instant.now());
    return simCardRepository.save(simCard);
  }

  /**
   * A Carrier of the Contract's tenant and of its Agent's Country, not archived. Another tenant's
   * Carrier reads as unknown, never as someone else's. Public (provision-request-details ticket):
   * {@code ProvisionSimRequestDetailsHandler} reuses this exact rule to validate a Provision SIM
   * Request's own Carrier choice at submission, rather than re-deriving it.
   */
  public Carrier requireUsableCarrier(Contract contract, UUID carrierId) {
    if (carrierId == null) {
      throw new InvalidRequestException("A SIM Card requires a carrierId");
    }
    Carrier carrier =
        carrierRepository
            .findByIdAndTenantId(carrierId, contract.getTenant().getId())
            .orElseThrow(() -> new InvalidRequestException("No Carrier with id " + carrierId));
    if (carrier.getCountry() != contract.getAgent().getCountry()) {
      throw new InvalidRequestException(
          "The Carrier " + carrier.getName() + " is not a Carrier of this Contract's Country");
    }
    if (carrier.isArchived()) {
      throw new InvalidRequestException("The Carrier " + carrier.getName() + " is archived");
    }
    return carrier;
  }

  /**
   * The Postpaid Plan a Postpaid SIM's monthly fee is copied from: an active Plan of the SIM
   * Card's own Carrier. A Prepaid SIM names none, and is refused if it does. Public
   * (provision-request-details ticket): shared with {@code ProvisionSimRequestDetailsHandler} for
   * the identical rule on a Provision SIM Request's own flavor/Plan choice at submission.
   */
  public PostpaidPlan requireUsablePlan(Carrier carrier, SimCardFlavor flavor, UUID postpaidPlanId) {
    if (flavor == SimCardFlavor.PREPAID) {
      if (postpaidPlanId != null) {
        throw new InvalidRequestException("A Prepaid SIM Card must not name a Postpaid Plan");
      }
      return null;
    }
    UUID planId = postpaidPlanId;
    if (planId == null) {
      throw new InvalidRequestException("A Postpaid SIM Card requires a postpaidPlanId");
    }
    PostpaidPlan plan =
        postpaidPlanRepository
            .findByIdAndCarrierId(planId, carrier.getId())
            .orElseThrow(
                () ->
                    new InvalidRequestException(
                        "No Postpaid Plan with id " + planId + " on the Carrier " + carrier.getName()));
    if (plan.isArchived()) {
      throw new InvalidRequestException("The Postpaid Plan " + plan.getName() + " is archived");
    }
    return plan;
  }
}
