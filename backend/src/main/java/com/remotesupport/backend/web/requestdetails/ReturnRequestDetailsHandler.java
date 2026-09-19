package com.remotesupport.backend.web.requestdetails;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Disposition;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.ReturnedUnit;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneOwner;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.repository.RequestRepository;
import com.remotesupport.backend.repository.ReturnedUnitRepository;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.web.InvalidRequestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A {@link RequestType#RETURN} Request's own detail (returns-and-agent-stock spec, Solution's
 * Return type and Disposition table; return-client-owned-smartphones ticket). Unlike every other
 * type this module validates, a Return names SEVERAL units at once — {@code
 * RequestDetailsInput#returnedSmartphoneIds()}/{@code returnedSimCardIds()} — each becoming its
 * own {@link ReturnedUnit} row (never more nullable columns on {@link Request}: see the
 * carrier-catalog-notes.md hard rule this ticket follows).
 *
 * <p>This ticket only accepts Client-owned Smartphones: any SIM Card, or a company-owned
 * Smartphone, is refused outright with a message that it isn't supported yet (ticket AC) — the
 * Manager-chosen Dispositions for company-owned units arrive in {@code
 * manager-decides-return-disposition}. Every accepted unit's Disposition is fixed here, at
 * submission, to {@link Disposition#POSTED_TO_CLIENT} (ticket AC).
 *
 * <p>Persists {@code request} itself before its {@link ReturnedUnit} rows: {@code request}'s own id
 * is already assigned (every entity in this codebase manually assigns its id before saving), and
 * every one of its non-optional fields (tenant, contract, tester, raisedByUser, type, status) is
 * already set by the caller before this handler runs — see {@link RequestController#create}'s
 * field-assignment order — so saving it here, rather than waiting for the caller's own {@code
 * requestRepository.save(request)} afterward, is safe and lets each {@code ReturnedUnit}'s
 * non-nullable {@code request} foreign key resolve within this same transaction. The caller's own
 * subsequent save is then a harmless no-op re-save of the same row.
 */
@Component
public class ReturnRequestDetailsHandler implements RequestDetailsHandler {

  private final SmartphoneRepository smartphoneRepository;
  private final SimCardRepository simCardRepository;
  private final RequestRepository requestRepository;
  private final ReturnedUnitRepository returnedUnitRepository;

  public ReturnRequestDetailsHandler(
      SmartphoneRepository smartphoneRepository,
      SimCardRepository simCardRepository,
      RequestRepository requestRepository,
      ReturnedUnitRepository returnedUnitRepository) {
    this.smartphoneRepository = smartphoneRepository;
    this.simCardRepository = simCardRepository;
    this.requestRepository = requestRepository;
    this.returnedUnitRepository = returnedUnitRepository;
  }

  @Override
  public RequestType type() {
    return RequestType.RETURN;
  }

  @Override
  public void apply(Contract contract, RequestDetailsInput input, Request request) {
    List<UUID> smartphoneIds = nullToEmpty(input.returnedSmartphoneIds());
    List<UUID> simCardIds = nullToEmpty(input.returnedSimCardIds());

    if (smartphoneIds.isEmpty() && simCardIds.isEmpty()) {
      throw new InvalidRequestException("A Return requires at least one unit");
    }
    requireNoDuplicates(smartphoneIds, "Smartphone");
    requireNoDuplicates(simCardIds, "SIM Card");

    // Validate every named unit before writing anything (no partial Return on a later failure):
    // build every row first, persist only once every one of them is known-good.
    List<ReturnedUnit> units = new ArrayList<>();
    for (UUID smartphoneId : smartphoneIds) {
      units.add(returnedSmartphoneUnit(contract, request, smartphoneId));
    }
    for (UUID simCardId : simCardIds) {
      units.add(returnedSimCardUnit(contract, request, simCardId));
    }

    requestRepository.save(request);
    returnedUnitRepository.saveAll(units);
  }

  private ReturnedUnit returnedSmartphoneUnit(Contract contract, Request request, UUID smartphoneId) {
    Smartphone smartphone =
        smartphoneRepository
            .findByIdAndContractId(smartphoneId, contract.getId())
            .orElseThrow(
                () -> new InvalidRequestException("No Smartphone with id " + smartphoneId + " on this Contract"));
    if (smartphone.getStatus() != SmartphoneStatus.ACTIVE) {
      throw new InvalidRequestException("The Smartphone " + smartphoneId + " to return must be Active");
    }
    if (smartphone.getOwner() != SmartphoneOwner.CLIENT) {
      throw new InvalidRequestException(
          "Returning a company-owned Smartphone isn't supported yet — only Client-owned Smartphones"
              + " can be returned for now");
    }

    ReturnedUnit unit = new ReturnedUnit();
    unit.setId(UUID.randomUUID());
    unit.setTenant(contract.getTenant());
    unit.setRequest(request);
    unit.setSmartphone(smartphone);
    unit.setDisposition(Disposition.POSTED_TO_CLIENT);
    unit.setCreatedAt(Instant.now());
    return unit;
  }

  private ReturnedUnit returnedSimCardUnit(Contract contract, Request request, UUID simCardId) {
    SimCard simCard =
        simCardRepository
            .findByIdAndContractId(simCardId, contract.getId())
            .orElseThrow(() -> new InvalidRequestException("No SIM Card with id " + simCardId + " on this Contract"));
    if (simCard.getStatus() != SimCardStatus.ACTIVE) {
      throw new InvalidRequestException("The SIM Card " + simCardId + " to return must be Active");
    }
    // CONTEXT.md "Owner": a SIM Card is always company-owned, so it always hits this ticket's
    // "not supported yet" refusal (ticket AC: "any SIM Card ... is refused").
    throw new InvalidRequestException(
        "Returning a company-owned unit isn't supported yet — only Client-owned Smartphones can be"
            + " returned for now");
  }

  private static List<UUID> nullToEmpty(List<UUID> ids) {
    return ids == null ? List.of() : ids;
  }

  private static void requireNoDuplicates(List<UUID> ids, String unitLabel) {
    Set<UUID> seen = new HashSet<>();
    for (UUID id : ids) {
      if (!seen.add(id)) {
        throw new InvalidRequestException("A Return can name each " + unitLabel + " at most once");
      }
    }
  }
}
