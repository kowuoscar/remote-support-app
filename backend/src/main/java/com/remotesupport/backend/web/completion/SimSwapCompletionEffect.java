package com.remotesupport.backend.web.completion;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.web.SimInstallationService;
import com.remotesupport.backend.web.requestdetails.SimSwapRequestDetailsHandler;
import org.springframework.stereotype.Component;

/**
 * Completing a {@link RequestType#SIM_SWAP} Request (spec.md Solution — Fleet changes on
 * completion: "SIM Swap: applies the moves. No Agent input."; sim-swap-moves ticket AC:
 * "Completing the Request applies the moves with no Agent input, through the Installed-in
 * module"). The moves themselves were already fixed at submission by {@code
 * SimSwapRequestDetailsHandler} onto {@code Request}'s {@code targetSimCard}/{@code
 * targetSmartphone} (and, for an exchange, {@code secondSimCard}/{@code secondTargetSmartphone});
 * this effect only reads them back and hands them to {@link SimInstallationService#applyMoves},
 * which re-validates each one against the Fleet as it is right now (ticket AC: "Completion
 * re-checks against the Fleet as it is then") and applies both moves atomically. A move that no
 * longer fits — the target Smartphone filled up, or either unit retired, in the meantime — makes
 * {@code applyMoves} throw, which fails this whole completion with that same clear message (ticket
 * AC: "refused with a clear message if the moves no longer fit"); nothing about the Request or the
 * Fleet changes when that happens.
 *
 * <p>A Request that already existed before this ticket has no {@code targetSimCard} of its own
 * (the older SIM Swap had no stored details at all), so it takes the no-op legacy path (ticket AC:
 * "A SIM Swap Request created before this ticket completes without changing the Fleet") —
 * mirroring the legacy branch every other completion effect in this package falls back to, just
 * with nothing left to do rather than an older full form to fill in.
 */
@Component
public class SimSwapCompletionEffect implements RequestCompletionEffect {

  private final SimInstallationService simInstallationService;

  public SimSwapCompletionEffect(SimInstallationService simInstallationService) {
    this.simInstallationService = simInstallationService;
  }

  @Override
  public RequestType type() {
    return RequestType.SIM_SWAP;
  }

  @Override
  public void apply(Contract contract, Request request, RequestCompletionInput input, AuthenticatedPrincipal principal) {
    if (request.getTargetSimCard() == null) {
      return;
    }
    simInstallationService.applyMoves(SimSwapRequestDetailsHandler.movesOf(request), request.getId(), principal);
  }
}
