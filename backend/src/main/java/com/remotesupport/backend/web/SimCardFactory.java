package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardFlavor;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.dto.SimCardCreateRequest;
import com.remotesupport.backend.repository.CarrierRepository;
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

  public SimCardFactory(SimCardRepository simCardRepository, CarrierRepository carrierRepository) {
    this.simCardRepository = simCardRepository;
    this.carrierRepository = carrierRepository;
  }

  /** Validates the request against {@code contract} and saves a new, Active SIM Card on its Fleet. */
  public SimCard create(Contract contract, SimCardCreateRequest request) {
    Carrier carrier = requireUsableCarrier(contract, request.carrierId());

    if (request.flavor() == SimCardFlavor.POSTPAID && request.monthlyFeeAmount() == null) {
      throw new InvalidRequestException("A Postpaid SIM Card requires a monthlyFeeAmount");
    }
    if (request.flavor() == SimCardFlavor.PREPAID && request.monthlyFeeAmount() != null) {
      throw new InvalidRequestException("A Prepaid SIM Card must not have a monthlyFeeAmount");
    }

    SimCard simCard = new SimCard();
    simCard.setId(UUID.randomUUID());
    simCard.setTenant(contract.getTenant());
    simCard.setContract(contract);
    simCard.setNumber(request.number());
    simCard.setCarrier(carrier);
    simCard.setFlavor(request.flavor());
    simCard.setMonthlyFeeAmount(request.monthlyFeeAmount());
    simCard.setStatus(SimCardStatus.ACTIVE);
    simCard.setCreatedAt(Instant.now());
    return simCardRepository.save(simCard);
  }

  /**
   * A Carrier of the Contract's tenant and of its Agent's Country, not archived. Another tenant's
   * Carrier reads as unknown, never as someone else's.
   */
  private Carrier requireUsableCarrier(Contract contract, UUID carrierId) {
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
}
