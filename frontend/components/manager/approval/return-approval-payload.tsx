import { DISPOSITION_LABEL, type DispositionValue, type ReturnedUnitItem, type RequestListItem } from "@/lib/api/types";
import type { ApprovalPayloadProps } from "./types";

/**
 * `RETURN`'s own approval-payload piece (spec.md Disposition table; agent-stock ticket AC: "The
 * Manager can choose Kept in Stock for a company-owned Smartphone or a SIM Card when approving a
 * Return") — moved out of `pending-request-decision-controls.tsx` into this per-type seam by the
 * feature's finisher pass (review finding), mirroring `components/agent/completion/`'s
 * render+body-builder registries.
 */

/** Two choices per unit kind. */
function dispositionOptionsFor(unit: ReturnedUnitItem): DispositionValue[] {
  return unit.smartphoneId ? ["POSTED_TO_COMPANY", "KEPT_IN_STOCK"] : ["CANCELLED", "KEPT_IN_STOCK"];
}

function unitLabel(unit: ReturnedUnitItem): string {
  return unit.smartphoneModel ?? unit.simCardNumber ?? "Unit";
}

/**
 * agent-stock ticket AC: "on a Postpaid SIM the picker notes that the carrier keeps charging with
 * no Client to bill" (spec.md user story 17) — shown next to a Postpaid SIM Card unit's picker
 * regardless of which Disposition is currently selected, so the Manager reads it before choosing.
 */
function isPostpaidSimCard(unit: ReturnedUnitItem): boolean {
  return !unit.smartphoneId && unit.simCardFlavor === "POSTPAID";
}

/** The units a Return still needs a Disposition for — a company-owned unit not yet decided. */
function unitsNeedingDisposition(request: RequestListItem): ReturnedUnitItem[] {
  return (request.returnedUnits ?? []).filter((unit) => !unit.disposition);
}

/**
 * Whether approving this Return needs its own form at all (registered as {@link
 * REQUEST_APPROVAL_NEEDS_FORM}'s `RETURN` entry) — a Return with nothing left to choose (every
 * unit already Client-owned, or already decided) approves exactly as any other type always has:
 * one click, no body.
 */
export function returnApprovalNeedsForm(request: RequestListItem): boolean {
  return unitsNeedingDisposition(request).length > 0;
}

/** Turns the picked `disposition-<unitId>` fields into the approve body's `dispositions` array. */
export function buildReturnApprovalBody(request: RequestListItem, formData: FormData): Record<string, unknown> {
  return {
    dispositions: unitsNeedingDisposition(request).map((unit) => ({
      returnedUnitId: unit.id,
      disposition: String(formData.get(`disposition-${unit.id}`)),
    })),
  };
}

/**
 * A picker per company-owned unit still missing its Disposition, plus a reminder next to a
 * Postpaid SIM Card's own picker (spec.md user story 17). Rendered inside
 * `PendingRequestDecisionControls`' own `<form>` — every `<select>` here is a plain named field
 * (`disposition-<unitId>`), read back by {@link buildReturnApprovalBody}.
 */
export function ReturnApprovalPayload({ request, disabled }: ApprovalPayloadProps) {
  return (
    <div className="flex flex-col items-end gap-1">
      {unitsNeedingDisposition(request).map((unit) => (
        <div key={unit.id} className="flex flex-col items-end gap-0.5">
          <label className="flex items-center gap-1.5 text-[11px] font-medium text-ink-secondary">
            {unitLabel(unit)}
            <select
              name={`disposition-${unit.id}`}
              required
              disabled={disabled}
              defaultValue={dispositionOptionsFor(unit)[0]}
              className="h-7 rounded-md border border-hairline-strong bg-canvas px-2 text-[12px] text-ink"
            >
              {dispositionOptionsFor(unit).map((disposition) => (
                <option key={disposition} value={disposition}>
                  {DISPOSITION_LABEL[disposition]}
                </option>
              ))}
            </select>
          </label>
          {isPostpaidSimCard(unit) ? (
            <span className="max-w-[220px] text-right text-[11px] text-warning">
              Kept in Stock: the carrier keeps charging with no Client to bill.
            </span>
          ) : null}
        </div>
      ))}
    </div>
  );
}
