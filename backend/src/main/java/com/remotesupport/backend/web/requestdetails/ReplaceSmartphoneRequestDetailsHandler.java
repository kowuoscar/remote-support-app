package com.remotesupport.backend.web.requestdetails;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.web.InvalidRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * A {@link RequestType#REPLACE_SMARTPHONE} Request's own detail (request-types-and-flow spec's
 * table: "Replace Smartphone — Required: the Smartphone to replace; Optional: requested model
 * (defaults to the old one's)"; replace-requests ticket AC: "A Replace Smartphone Request requires
 * an Active Smartphone of its Contract and may name a different model"). Reuses {@code
 * targetSmartphoneId} on {@link RequestDetailsInput} — the same field {@link
 * RebootRequestDetailsHandler}'s own (required) target and Provision SIM's own (optional) target
 * already use, and the same Active-and-of-this-Contract rule they enforce — and {@code
 * requestedModel}, the same field {@code ProvisionSmartphoneRequestDetailsHandler} uses for its
 * own (required) model, here optional: no type ever sets both a Reboot/Provision-SIM target and a
 * Replace target on the same Request, and no type ever needs both a Provision Smartphone's
 * required model and a Replace Smartphone's optional one, so both fields double up cleanly (see
 * {@link Request}'s own Javadoc on each).
 */
@Component
public class ReplaceSmartphoneRequestDetailsHandler implements RequestDetailsHandler {

  private final SmartphoneRepository smartphoneRepository;

  public ReplaceSmartphoneRequestDetailsHandler(SmartphoneRepository smartphoneRepository) {
    this.smartphoneRepository = smartphoneRepository;
  }

  @Override
  public RequestType type() {
    return RequestType.REPLACE_SMARTPHONE;
  }

  @Override
  public void apply(Contract contract, RequestDetailsInput input, Request request) {
    if (input.targetSmartphoneId() == null) {
      throw new InvalidRequestException("A Replace Smartphone Request requires a targetSmartphoneId");
    }
    Smartphone smartphone =
        smartphoneRepository
            .findByIdAndContractId(input.targetSmartphoneId(), contract.getId())
            .orElseThrow(
                () ->
                    new InvalidRequestException(
                        "No Smartphone with id " + input.targetSmartphoneId() + " on this Contract"));
    if (smartphone.getStatus() != SmartphoneStatus.ACTIVE) {
      throw new InvalidRequestException("The Smartphone to replace must be Active");
    }
    request.setTargetSmartphone(smartphone);

    if (StringUtils.hasText(input.requestedModel())) {
      request.setRequestedModel(input.requestedModel().trim());
    }
  }
}
