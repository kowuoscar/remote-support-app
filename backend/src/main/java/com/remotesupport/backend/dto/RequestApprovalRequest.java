package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Disposition;
import java.util.List;
import java.util.UUID;

/**
 * The Manager's approval body — empty for a Provision/Replace Request (still approves with no
 * further input; {@code RequestByIdController#approve} accepts this as optional, {@code required =
 * false}, so a caller sending no body at all keeps working). {@code dispositions}
 * (manager-decides-return-disposition ticket, spec.md Solution's Disposition table) is where a
 * {@code RETURN} Request's Manager-chosen Dispositions go, added to this same type rather than a
 * new endpoint or a breaking change to this one (see the original Javadoc this replaces, on the
 * return-client-owned-smartphones ticket that first shaped this record this way): one entry per
 * company-owned unit still missing a Disposition, naming that unit's own {@code ReturnedUnit} row
 * id — a Client-owned unit's Disposition is fixed at submission and can never be named here (ticket
 * AC: "Dispositions can't be changed after approval").
 */
public record RequestApprovalRequest(List<UnitDisposition> dispositions) {

  /** One company-owned unit's chosen Disposition, keyed by its own {@code ReturnedUnit} row id. */
  public record UnitDisposition(UUID returnedUnitId, Disposition disposition) {}
}
