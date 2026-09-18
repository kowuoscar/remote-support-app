package com.remotesupport.backend.web.requestdetails;

import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.PostpaidPlan;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.web.InvalidRequestException;
import com.remotesupport.backend.web.SimCardFactory;
import org.springframework.stereotype.Component;

/**
 * A {@link RequestType#PROVISION_SIM} Request's own detail (request-types-and-flow spec's table:
 * "Provision SIM — Required: flavor, Carrier, and a Postpaid Plan of that Carrier when postpaid;
 * Optional: target Smartphone"; provision-request-details ticket). Reuses {@link
 * SimCardFactory#requireUsableCarrier}/{@link SimCardFactory#requireUsablePlan} rather than
 * re-deriving the Carrier/Plan rule — "ALL SIM validation lives in SimCardFactory" (sim-card-
 * carrier ticket) applies just as much to a Request that will provision a SIM Card later as to one
 * created directly. The optional target Smartphone reuses {@code targetSmartphoneId} on {@link
 * RequestDetailsInput} and the same Active-and-of-this-Contract rule {@link
 * RebootRequestDetailsHandler} already enforces for Reboot's own (required) target.
 */
@Component
public class ProvisionSimRequestDetailsHandler implements RequestDetailsHandler {

  private final SimCardFactory simCardFactory;
  private final SmartphoneRepository smartphoneRepository;

  public ProvisionSimRequestDetailsHandler(
      SimCardFactory simCardFactory, SmartphoneRepository smartphoneRepository) {
    this.simCardFactory = simCardFactory;
    this.smartphoneRepository = smartphoneRepository;
  }

  @Override
  public RequestType type() {
    return RequestType.PROVISION_SIM;
  }

  @Override
  public void apply(Contract contract, RequestDetailsInput input, Request request) {
    if (input.flavor() == null) {
      throw new InvalidRequestException("A Provision SIM Request requires a flavor");
    }
    Carrier carrier = simCardFactory.requireUsableCarrier(contract, input.carrierId());
    PostpaidPlan plan = simCardFactory.requireUsablePlan(carrier, input.flavor(), input.postpaidPlanId());

    request.setRequestedFlavor(input.flavor());
    request.setRequestedCarrier(carrier);
    request.setRequestedPostpaidPlan(plan);

    if (input.targetSmartphoneId() != null) {
      Smartphone smartphone =
          smartphoneRepository
              .findByIdAndContractId(input.targetSmartphoneId(), contract.getId())
              .orElseThrow(
                  () ->
                      new InvalidRequestException(
                          "No Smartphone with id " + input.targetSmartphoneId() + " on this Contract"));
      if (smartphone.getStatus() != SmartphoneStatus.ACTIVE) {
        throw new InvalidRequestException("The target Smartphone must be Active");
      }
      request.setTargetSmartphone(smartphone);
    }
  }
}
