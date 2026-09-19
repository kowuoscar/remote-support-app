package com.remotesupport.backend.web.requestdetails;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.web.InvalidRequestException;
import com.remotesupport.backend.web.SimInstallationService;
import com.remotesupport.backend.web.SimInstallationService.Move;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A {@link RequestType#SIM_SWAP} Request's own detail (request-types-and-flow spec's table: "SIM
 * Swap — one move (SIM Card → Smartphone), or an exchange: two SIM Cards installed in two
 * different Smartphones"; sim-swap-moves ticket). A move names the SIM Card and its destination
 * Smartphone directly ({@code targetSimCardId}/{@code targetSmartphoneId}); an exchange names two
 * SIM Cards ({@code targetSimCardId}/{@code secondSimCardId}) and this handler derives each one's
 * destination from where the *other* one currently sits — the Tester or Agent never names a
 * Smartphone for an exchange. Both shapes are fixed at submission onto {@link
 * Request#getTargetSimCard()}/{@link Request#getTargetSmartphone()} (the first move) and, for an
 * exchange only, {@link Request#getSecondSimCard()}/{@link Request#getSecondTargetSmartphone()}
 * (the second) — {@code SimSwapCompletionEffect} applies exactly those stored moves later, never
 * re-deriving them.
 *
 * <p>Submission also checks the resulting moves fit the two-SIM-per-Smartphone limit right now,
 * via {@link SimInstallationService#checkMovesFit} — the same dry-run check {@code applyMoves}
 * itself does before writing anything, so a move that's invalid at submission is refused before
 * the Request is ever saved (spec.md: "Submission checks that applying the moves leaves no
 * Smartphone with more than two SIM Cards").
 */
@Component
public class SimSwapRequestDetailsHandler implements RequestDetailsHandler {

  private final SimCardRepository simCardRepository;
  private final SmartphoneRepository smartphoneRepository;
  private final SimInstallationService simInstallationService;

  public SimSwapRequestDetailsHandler(
      SimCardRepository simCardRepository,
      SmartphoneRepository smartphoneRepository,
      SimInstallationService simInstallationService) {
    this.simCardRepository = simCardRepository;
    this.smartphoneRepository = smartphoneRepository;
    this.simInstallationService = simInstallationService;
  }

  @Override
  public RequestType type() {
    return RequestType.SIM_SWAP;
  }

  @Override
  public void apply(Contract contract, RequestDetailsInput input, Request request) {
    if (input.targetSimCardId() == null) {
      throw new InvalidRequestException("A SIM Swap Request requires a targetSimCardId");
    }
    SimCard first = requireActiveSimCard(contract, input.targetSimCardId());

    if (input.secondSimCardId() != null) {
      applyExchange(contract, input, request, first);
    } else {
      applyMove(contract, input, request, first);
    }

    simInstallationService.checkMovesFit(movesOf(request));
  }

  /** A plain single move: {@code simCard} into an explicitly named destination Smartphone. */
  private void applyMove(Contract contract, RequestDetailsInput input, Request request, SimCard simCard) {
    if (input.targetSmartphoneId() == null) {
      throw new InvalidRequestException("A SIM Swap move requires a targetSmartphoneId");
    }
    Smartphone target = requireActiveSmartphone(contract, input.targetSmartphoneId());
    Smartphone current = simCard.getInstalledInSmartphone();
    if (current != null && current.getId().equals(target.getId())) {
      throw new InvalidRequestException(
          "This move changes nothing — the SIM Card is already installed in that Smartphone");
    }
    request.setTargetSimCard(simCard);
    request.setTargetSmartphone(target);
  }

  /**
   * An exchange: {@code first} and the named {@code secondSimCardId} must each be Installed in a
   * different Smartphone (spec.md: "an exchange of two SIM Cards installed in two different
   * Smartphones") — their destinations are each other's current Smartphone, never named directly.
   */
  private void applyExchange(Contract contract, RequestDetailsInput input, Request request, SimCard first) {
    if (input.secondSimCardId().equals(input.targetSimCardId())) {
      throw new InvalidRequestException("A SIM Swap exchange requires two different SIM Cards");
    }
    SimCard second = requireActiveSimCard(contract, input.secondSimCardId());

    Smartphone firstSmartphone = first.getInstalledInSmartphone();
    Smartphone secondSmartphone = second.getInstalledInSmartphone();
    if (firstSmartphone == null || secondSmartphone == null) {
      throw new InvalidRequestException("Both SIM Cards must be installed in a Smartphone to exchange them");
    }
    if (firstSmartphone.getId().equals(secondSmartphone.getId())) {
      throw new InvalidRequestException(
          "An exchange requires the two SIM Cards to be installed in two different Smartphones");
    }

    request.setTargetSimCard(first);
    request.setTargetSmartphone(secondSmartphone);
    request.setSecondSimCard(second);
    request.setSecondTargetSmartphone(firstSmartphone);
  }

  private SimCard requireActiveSimCard(Contract contract, UUID simCardId) {
    SimCard simCard =
        simCardRepository
            .findByIdAndContractId(simCardId, contract.getId())
            .orElseThrow(
                () -> new InvalidRequestException("No SIM Card with id " + simCardId + " on this Contract"));
    if (simCard.getStatus() != SimCardStatus.ACTIVE) {
      throw new InvalidRequestException("The SIM Card must be Active");
    }
    return simCard;
  }

  private Smartphone requireActiveSmartphone(Contract contract, UUID smartphoneId) {
    Smartphone smartphone =
        smartphoneRepository
            .findByIdAndContractId(smartphoneId, contract.getId())
            .orElseThrow(
                () ->
                    new InvalidRequestException(
                        "No Smartphone with id " + smartphoneId + " on this Contract"));
    if (smartphone.getStatus() != SmartphoneStatus.ACTIVE) {
      throw new InvalidRequestException("The target Smartphone must be Active");
    }
    return smartphone;
  }

  /**
   * The one or two moves {@code request} now carries, in the shared {@link SimInstallationService}
   * shape — read by {@code SimSwapCompletionEffect} (a different package) at completion time to
   * apply exactly what was fixed at submission, never re-derived.
   */
  public static List<Move> movesOf(Request request) {
    Move firstMove = new Move(request.getTargetSimCard(), request.getTargetSmartphone());
    return request.getSecondSimCard() == null
        ? List.of(firstMove)
        : List.of(firstMove, new Move(request.getSecondSimCard(), request.getSecondTargetSmartphone()));
  }
}
