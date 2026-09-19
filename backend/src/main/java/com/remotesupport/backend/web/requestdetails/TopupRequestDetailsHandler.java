package com.remotesupport.backend.web.requestdetails;

import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.domain.TopupOption;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.repository.TopupOptionRepository;
import com.remotesupport.backend.web.InvalidRequestException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A {@link RequestType#TOPUP} Request's own detail (request-types-and-flow spec's table: "Topup —
 * Required: target SIM Card; a Topup Option of that SIM Card's Carrier when the Carrier has an
 * active one, otherwise a description"; reboot-and-topup-details ticket). Self-contained like
 * {@link RebootRequestDetailsHandler}: this file plus {@code targetSimCardId}/{@code
 * topupOptionId} on {@link RequestDetailsInput} is the whole slice.
 *
 * <p>The fallback description reuses {@link Request#getDescription()} directly rather than a
 * field of its own — every Request already carries an optional description
 * (other-replaces-repair ticket), and Topup simply makes it conditionally required instead of
 * introducing a second text field with the same meaning.
 */
@Component
public class TopupRequestDetailsHandler implements RequestDetailsHandler {

  private final SimCardRepository simCardRepository;
  private final TopupOptionRepository topupOptionRepository;

  public TopupRequestDetailsHandler(
      SimCardRepository simCardRepository, TopupOptionRepository topupOptionRepository) {
    this.simCardRepository = simCardRepository;
    this.topupOptionRepository = topupOptionRepository;
  }

  @Override
  public RequestType type() {
    return RequestType.TOPUP;
  }

  @Override
  public void apply(Contract contract, RequestDetailsInput input, Request request) {
    if (input.targetSimCardId() == null) {
      throw new InvalidRequestException("A Topup Request requires a targetSimCardId");
    }
    SimCard simCard =
        simCardRepository
            .findByIdAndContractId(input.targetSimCardId(), contract.getId())
            .orElseThrow(
                () ->
                    new InvalidRequestException(
                        "No SIM Card with id " + input.targetSimCardId() + " on this Contract"));
    if (simCard.getStatus() != SimCardStatus.ACTIVE) {
      throw new InvalidRequestException("The target SIM Card must be Active");
    }
    request.setTargetSimCard(simCard);

    Carrier carrier = simCard.getCarrier();
    boolean carrierHasAnActiveOption =
        carrier != null
            && topupOptionRepository.findByCarrierId(carrier.getId()).stream()
                .anyMatch(option -> !option.isArchived());

    if (input.topupOptionId() != null) {
      request.setTopupOption(requireUsableOption(carrier, input.topupOptionId()));
    } else if (carrierHasAnActiveOption) {
      throw new InvalidRequestException(
          "A topupOptionId is required — this SIM Card's Carrier has an active Topup Option");
    } else if (request.getDescription() == null) {
      throw new InvalidRequestException(
          "A description is required — this SIM Card's Carrier has no active Topup Option");
    }
  }

  /** An active Option of this SIM Card's own Carrier — another Carrier's reads as unknown. */
  private TopupOption requireUsableOption(Carrier carrier, UUID topupOptionId) {
    TopupOption option =
        topupOptionRepository
            .findById(topupOptionId)
            .filter(candidate -> carrier != null && candidate.getCarrier().getId().equals(carrier.getId()))
            .orElseThrow(
                () ->
                    new InvalidRequestException(
                        "No Topup Option with id "
                            + topupOptionId
                            + " on this SIM Card's Carrier"));
    if (option.isArchived()) {
      throw new InvalidRequestException("This Topup Option is archived");
    }
    return option;
  }
}
