package com.remotesupport.backend.web.requestdetails;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.web.InvalidRequestException;
import org.springframework.stereotype.Component;

/**
 * A {@link RequestType#REPLACE_SIM} Request's own detail (request-types-and-flow spec's table:
 * "Replace SIM — Required: the SIM Card to replace"; replace-requests ticket AC: "A Replace SIM
 * Request requires an Active SIM Card of its Contract"). Reuses {@code targetSimCardId} on {@link
 * RequestDetailsInput} — the same field {@code TopupRequestDetailsHandler} uses for its own target
 * — and the same Active-and-of-this-Contract rule it enforces; no type ever sets both a Topup
 * target and a Replace SIM target on the same Request. Unlike Provision SIM, the new SIM Card's
 * own Carrier/flavor/Plan are never named here — they're decided at completion, defaulted from
 * this old one's current values (ticket AC), not stored on the Request at submission.
 */
@Component
public class ReplaceSimRequestDetailsHandler implements RequestDetailsHandler {

  private final SimCardRepository simCardRepository;

  public ReplaceSimRequestDetailsHandler(SimCardRepository simCardRepository) {
    this.simCardRepository = simCardRepository;
  }

  @Override
  public RequestType type() {
    return RequestType.REPLACE_SIM;
  }

  @Override
  public void apply(Contract contract, RequestDetailsInput input, Request request) {
    if (input.targetSimCardId() == null) {
      throw new InvalidRequestException("A Replace SIM Request requires a targetSimCardId");
    }
    SimCard simCard =
        simCardRepository
            .findByIdAndContractId(input.targetSimCardId(), contract.getId())
            .orElseThrow(
                () ->
                    new InvalidRequestException(
                        "No SIM Card with id " + input.targetSimCardId() + " on this Contract"));
    if (simCard.getStatus() != SimCardStatus.ACTIVE) {
      throw new InvalidRequestException("The SIM Card to replace must be Active");
    }
    request.setTargetSimCard(simCard);
  }
}
