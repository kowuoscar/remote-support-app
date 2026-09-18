package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one module that owns installing, uninstalling and the two-SIM-per-Smartphone check
 * (sim-installed-in-smartphone ticket AC), so no path derives its own copy of the rule. Every
 * write to {@link SimCard#getInstalledInSmartphone()} goes through here: {@link SimCardController}
 * for a Fleet-page set/clear, {@link SmartphoneController} and {@link ProvisioningService} for the
 * retirement cascade (spec.md Solution — Fleet model: "Retiring a Smartphone clears the link on
 * its SIM Cards; retiring a SIM Card clears its own"), and, per the spec's Execution order, later
 * tickets: {@code provision-request-details} (a new SIM's target Smartphone), {@code
 * replace-requests} (carrying a replaced Smartphone's SIM Cards onto its replacement) and {@code
 * sim-swap-moves} (a SIM Swap's move or exchange) all call {@link #applyMoves} so several moves
 * land atomically and the two-SIM check sees the Fleet as it will be once every move in the same
 * Request applies — never a false positive from checking one move's intermediate state, as an
 * exchange would trip if checked SIM-Card-by-SIM-Card.
 */
@Component
public class SimInstallationService {

  private final SimCardRepository simCardRepository;

  public SimInstallationService(SimCardRepository simCardRepository) {
    this.simCardRepository = simCardRepository;
  }

  /**
   * One SIM Card's destination: a Smartphone to install (or move) it into, or {@code null} to
   * uninstall it.
   */
  public record Move(SimCard simCard, Smartphone smartphone) {}

  /**
   * Installs {@code simCard} into {@code smartphone}, moving it out of wherever it was before.
   * {@code requestId} is the Request whose completion caused this, or {@code null} for a
   * Fleet-page action with no Request behind it.
   */
  public void install(
      SimCard simCard, Smartphone smartphone, UUID requestId, AuthenticatedPrincipal principal) {
    applyMoves(List.of(new Move(simCard, smartphone)), requestId, principal);
  }

  /** Uninstalls {@code simCard} from wherever it currently is; a no-op if it wasn't installed. */
  public void uninstall(SimCard simCard, UUID requestId, AuthenticatedPrincipal principal) {
    applyMoves(List.of(new Move(simCard, null)), requestId, principal);
  }

  /**
   * Clears the Installed-in link on every SIM Card currently in {@code smartphone} — the
   * retirement side-effect (spec.md Solution — Fleet model). {@code requestId} is the Request
   * that caused the retirement, or {@code null} for a Fleet-page status change.
   */
  public void clearLinksForRetiredSmartphone(
      Smartphone smartphone, UUID requestId, AuthenticatedPrincipal principal) {
    List<SimCard> installed = simCardRepository.findByInstalledInSmartphoneId(smartphone.getId());
    if (installed.isEmpty()) {
      return;
    }
    applyMoves(installed.stream().map(simCard -> new Move(simCard, null)).toList(), requestId, principal);
  }

  /** Clears {@code simCard}'s own Installed-in link, if any — the retirement side-effect. */
  public void clearLinkForRetiredSimCard(SimCard simCard, UUID requestId, AuthenticatedPrincipal principal) {
    if (simCard.getInstalledInSmartphone() == null) {
      return;
    }
    applyMoves(List.of(new Move(simCard, null)), requestId, principal);
  }

  /**
   * Applies every move together: each is validated against the Fleet as it will be once the whole
   * batch lands (an exchange never trips the two-SIM check on an intermediate state), then, only
   * if every move is valid, all are applied and audited. Nothing is written if any move fails.
   */
  @Transactional
  public void applyMoves(List<Move> moves, UUID requestId, AuthenticatedPrincipal principal) {
    if (moves.isEmpty()) {
      return;
    }

    moves.forEach(this::validateMove);

    Set<UUID> movedSimCardIds = new HashSet<>();
    for (Move move : moves) {
      movedSimCardIds.add(move.simCard().getId());
    }

    Map<UUID, Smartphone> targets = new HashMap<>();
    Map<UUID, Long> incomingCountByTarget = new HashMap<>();
    for (Move move : moves) {
      Smartphone target = move.smartphone();
      if (target == null) {
        continue;
      }
      targets.put(target.getId(), target);
      incomingCountByTarget.merge(target.getId(), 1L, Long::sum);
    }

    for (Map.Entry<UUID, Long> entry : incomingCountByTarget.entrySet()) {
      Smartphone target = targets.get(entry.getKey());
      long alreadyThere =
          simCardRepository.findByInstalledInSmartphoneId(target.getId()).stream()
              .filter(simCard -> !movedSimCardIds.contains(simCard.getId()))
              .count();
      if (alreadyThere + entry.getValue() > 2) {
        throw new ConflictException(
            "The Smartphone " + target.getModel() + " already holds two SIM Cards");
      }
    }

    for (Move move : moves) {
      applyOneMove(move, requestId, principal);
    }
  }

  private void applyOneMove(Move move, UUID requestId, AuthenticatedPrincipal principal) {
    SimCard simCard = move.simCard();
    Smartphone oldSmartphone = simCard.getInstalledInSmartphone();
    Smartphone newSmartphone = move.smartphone();
    if (Objects.equals(id(oldSmartphone), id(newSmartphone))) {
      return;
    }

    simCard.setInstalledInSmartphone(newSmartphone);
    simCardRepository.save(simCard);

    if (oldSmartphone != null) {
      AuditLog.simCardUninstalled(
          simCard.getId(), oldSmartphone.getId(), requestId, principal.userId(), principal.tenantId());
    }
    if (newSmartphone != null) {
      AuditLog.simCardInstalled(
          simCard.getId(), newSmartphone.getId(), requestId, principal.userId(), principal.tenantId());
    }
  }

  /**
   * A Smartphone to install into must be Active and of the same Contract as the SIM Card
   * (sim-installed-in-smartphone ticket AC); a retired SIM Card can't be installed either. A
   * {@code null} target (uninstalling) needs no validation.
   */
  private void validateMove(Move move) {
    Smartphone smartphone = move.smartphone();
    if (smartphone == null) {
      return;
    }
    SimCard simCard = move.simCard();
    if (!smartphone.getContract().getId().equals(simCard.getContract().getId())) {
      throw new InvalidRequestException(
          "No Smartphone with id " + smartphone.getId() + " on this SIM Card's Contract");
    }
    if (smartphone.getStatus() != SmartphoneStatus.ACTIVE) {
      throw new InvalidRequestException(
          "Cannot install a SIM Card into a Smartphone that is " + smartphone.getStatus());
    }
    if (simCard.getStatus() != SimCardStatus.ACTIVE) {
      throw new InvalidRequestException("Cannot install a SIM Card that is " + simCard.getStatus());
    }
  }

  private static UUID id(Smartphone smartphone) {
    return smartphone == null ? null : smartphone.getId();
  }
}
