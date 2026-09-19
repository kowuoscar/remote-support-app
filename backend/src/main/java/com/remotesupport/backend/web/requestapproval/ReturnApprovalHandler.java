package com.remotesupport.backend.web.requestapproval;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Disposition;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.ReturnedUnit;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneOwner;
import com.remotesupport.backend.dto.RequestApprovalRequest;
import com.remotesupport.backend.dto.RequestCreateRequest;
import com.remotesupport.backend.repository.ReturnedUnitRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.web.InvalidRequestException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link RequestType#RETURN}'s own approval rule (returns-and-agent-stock spec, Solution:
 * "Approval"/"Disposition"; manager-decides-return-disposition ticket, moved into this seam by the
 * feature's finisher pass so {@code RequestController}/{@code RequestByIdController} no longer
 * know Return exists).
 */
@Component
public class ReturnApprovalHandler implements RequestApprovalHandler {

  private final SmartphoneRepository smartphoneRepository;
  private final ReturnedUnitRepository returnedUnitRepository;

  public ReturnApprovalHandler(
      SmartphoneRepository smartphoneRepository, ReturnedUnitRepository returnedUnitRepository) {
    this.smartphoneRepository = smartphoneRepository;
    this.returnedUnitRepository = returnedUnitRepository;
  }

  @Override
  public RequestType type() {
    return RequestType.RETURN;
  }

  /**
   * Whether a {@code RETURN} Request's own named units hold at least one company-owned one — any
   * SIM Card (CONTEXT.md "Owner": always company-owned), or a company-owned Smartphone — which is
   * what sends the whole Request to Pending Approval (spec.md Solution: "Approval"), whoever raised
   * it. Looked up directly off the creation body's raw id lists rather than the {@link
   * ReturnedUnit} rows {@code ReturnRequestDetailsHandler} builds moments later — this only needs
   * to know "is approval required", not build or validate anything, and it must run before {@code
   * request.setStatus} is set, ahead of that handler.
   */
  @Override
  public boolean requiresApproval(Contract contract, RequestCreateRequest requestBody) {
    List<UUID> simCardIds = requestBody.returnedSimCardIds();
    if (simCardIds != null && !simCardIds.isEmpty()) {
      return true;
    }
    List<UUID> smartphoneIds = requestBody.returnedSmartphoneIds();
    if (smartphoneIds == null) {
      return false;
    }
    for (UUID smartphoneId : smartphoneIds) {
      Smartphone smartphone = smartphoneRepository.findByIdAndContractId(smartphoneId, contract.getId()).orElse(null);
      if (smartphone != null && smartphone.getOwner() == SmartphoneOwner.COMPANY) {
        return true;
      }
    }
    return false;
  }

  /**
   * Applies a {@code RETURN} Request's Manager-chosen Dispositions at the moment of approval
   * (spec.md Solution: "Approving a Return requires a Disposition for every company-owned unit in
   * the same action; without them the approval is refused") — a Client-owned unit already carries
   * its fixed {@link Disposition#POSTED_TO_CLIENT} from submission ({@code
   * ReturnRequestDetailsHandler}) and can't be named here (ticket AC: "Dispositions can't be
   * changed after approval"); every other named unit must be, exactly once, with a Disposition
   * that fits its own kind — a Smartphone {@link Disposition#POSTED_TO_COMPANY} or {@link
   * Disposition#KEPT_IN_STOCK}, a SIM Card {@link Disposition#CANCELLED} or {@link
   * Disposition#KEPT_IN_STOCK}. Returns a log-friendly summary of what was chosen, for {@link
   * com.remotesupport.backend.logging.AuditLog#requestApproved}.
   */
  @Override
  public String applyApproval(Request request, RequestApprovalRequest requestBody) {
    List<ReturnedUnit> units = returnedUnitRepository.forRequest(request);

    Map<UUID, Disposition> chosen = new HashMap<>();
    if (requestBody != null && requestBody.dispositions() != null) {
      for (RequestApprovalRequest.UnitDisposition entry : requestBody.dispositions()) {
        chosen.put(entry.returnedUnitId(), entry.disposition());
      }
    }

    List<ReturnedUnit> toSave = new ArrayList<>();
    List<String> logged = new ArrayList<>();
    for (ReturnedUnit unit : units) {
      if (unit.getDisposition() != null) {
        if (chosen.containsKey(unit.getId())) {
          throw new InvalidRequestException(
              "The unit " + unit.getId() + " already has a Disposition and it cannot be changed");
        }
        continue;
      }

      Disposition disposition = chosen.get(unit.getId());
      if (disposition == null) {
        throw new InvalidRequestException("A Disposition is required for every company-owned unit, including " + unit.getId());
      }
      if (unit.getSmartphone() != null
          && disposition != Disposition.POSTED_TO_COMPANY
          && disposition != Disposition.KEPT_IN_STOCK) {
        throw new InvalidRequestException(
            "A company-owned Smartphone's Disposition must be Posted to company or Kept in Stock, not " + disposition);
      }
      if (unit.getSimCard() != null
          && disposition != Disposition.CANCELLED
          && disposition != Disposition.KEPT_IN_STOCK) {
        throw new InvalidRequestException(
            "A SIM Card's Disposition must be Cancelled or Kept in Stock, not " + disposition);
      }

      unit.setDisposition(disposition);
      toSave.add(unit);
      logged.add(unit.getId() + "=" + disposition.name());
    }
    returnedUnitRepository.saveAll(toSave);
    return String.join(",", logged);
  }
}
