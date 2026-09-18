package com.remotesupport.backend.web.requestdetails;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.web.InvalidRequestException;
import org.springframework.stereotype.Component;

/**
 * A {@link RequestType#REBOOT} Request's own detail (request-types-and-flow spec's table: "Reboot
 * — Required: target Smartphone"; reboot-and-topup-details ticket AC: "A Reboot Request requires
 * an Active Smartphone of its Contract"). Self-contained: this file plus its {@code
 * targetSmartphoneId} field on {@link RequestDetailsInput} is the whole slice — nothing else in
 * the codebase needs to change for Reboot's details to work on both creation paths.
 */
@Component
public class RebootRequestDetailsHandler implements RequestDetailsHandler {

  private final SmartphoneRepository smartphoneRepository;

  public RebootRequestDetailsHandler(SmartphoneRepository smartphoneRepository) {
    this.smartphoneRepository = smartphoneRepository;
  }

  @Override
  public RequestType type() {
    return RequestType.REBOOT;
  }

  @Override
  public void apply(Contract contract, RequestDetailsInput input, Request request) {
    if (input.targetSmartphoneId() == null) {
      throw new InvalidRequestException("A Reboot Request requires a targetSmartphoneId");
    }
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
