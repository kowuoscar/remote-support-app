import {
  DISPOSITION_LABEL,
  SIM_CARD_FLAVOR_LABEL,
  type RequestListItem,
  type RequestTypeValue,
} from "@/lib/api/types";

type SummaryRenderer = (request: RequestListItem) => string | null;

/**
 * One "details summary" renderer per type, for the Requests lists (reboot-and-topup-details
 * ticket AC: "The Tester's and the Agent's Requests lists summarise the details on each row").
 * A later ticket adding a type's details adds one entry here alongside its `registry.tsx` entry;
 * a type with none yet (or a legacy Request that predates this ticket, so its fields are all
 * absent) renders nothing extra — the row still shows its type and, where given, its description,
 * exactly as before this ticket.
 */
const REQUEST_DETAILS_SUMMARY: Partial<Record<RequestTypeValue, SummaryRenderer>> = {
  REBOOT: (request) =>
    request.targetSmartphoneModel ? `Smartphone: ${request.targetSmartphoneModel}` : null,
  TOPUP: (request) => {
    if (!request.targetSimCardNumber) return null;
    return request.topupOptionName
      ? `SIM Card: ${request.targetSimCardNumber} · ${request.topupOptionName}`
      : `SIM Card: ${request.targetSimCardNumber}`;
  },
  SIM_SWAP: (request) => {
    if (!request.targetSimCardNumber || !request.targetSmartphoneModel) return null;
    const firstMove = `SIM ${request.targetSimCardNumber} into ${request.targetSmartphoneModel}`;
    if (!request.secondSimCardNumber || !request.secondTargetSmartphoneModel) return firstMove;
    return `${firstMove}; SIM ${request.secondSimCardNumber} into ${request.secondTargetSmartphoneModel}`;
  },
  PROVISION_SMARTPHONE: (request) =>
    request.requestedModel ? `Requested: ${request.requestedModel}` : null,
  PROVISION_SIM: (request) => {
    if (!request.requestedFlavor) return null;
    const parts = [SIM_CARD_FLAVOR_LABEL[request.requestedFlavor]];
    if (request.requestedCarrierName) parts.push(request.requestedCarrierName);
    if (request.requestedPostpaidPlanName) parts.push(request.requestedPostpaidPlanName);
    const line = `New SIM: ${parts.join(" · ")}`;
    return request.targetSmartphoneModel ? `${line} → ${request.targetSmartphoneModel}` : line;
  },
  // replace-requests ticket AC: "The Requests lists name the unit being replaced".
  REPLACE_SMARTPHONE: (request) => {
    if (!request.targetSmartphoneModel) return null;
    return request.requestedModel
      ? `Replacing: ${request.targetSmartphoneModel} → ${request.requestedModel}`
      : `Replacing: ${request.targetSmartphoneModel}`;
  },
  REPLACE_SIM: (request) =>
    request.targetSimCardNumber ? `Replacing: ${request.targetSimCardNumber}` : null,
  // return-client-owned-smartphones ticket AC: "Each unit carries the Disposition ... shown on
  // the Request in every Requests list".
  RETURN: (request) => {
    if (!request.returnedUnits || request.returnedUnits.length === 0) return null;
    return request.returnedUnits
      .map((unit) => {
        const label = unit.smartphoneModel ?? unit.simCardNumber ?? "unit";
        const disposition = unit.disposition ? DISPOSITION_LABEL[unit.disposition] : "Disposition pending";
        return `${label} → ${disposition}`;
      })
      .join("; ");
  },
};

/** The one-line details summary for `request`'s row, or `null` when its type has none. */
export function requestDetailsSummary(request: RequestListItem): string | null {
  return REQUEST_DETAILS_SUMMARY[request.type]?.(request) ?? null;
}
