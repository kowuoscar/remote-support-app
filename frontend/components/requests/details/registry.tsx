import type { ComponentType } from "react";
import type { RequestTypeValue } from "@/lib/api/types";
import { RebootRequestDetails } from "./reboot-request-details";
import { TopupRequestDetails } from "./topup-request-details";
import type { RequestDetailsProps } from "./types";

/**
 * The one registry a later ticket extends to give another Request type its own details section
 * (reboot-and-topup-details ticket, "adding a type means adding a file and a registry entry" —
 * spec.md Solution: "ONE details component per Request type ... used by both the Tester's submit
 * dialog and the Agent's log-Request dialog"). A type with no entry here falls back to the
 * dialogs' own generic, type-agnostic description field — exactly what every type but Reboot and
 * Topup still does as of this ticket.
 */
export const REQUEST_DETAILS_COMPONENTS: Partial<Record<RequestTypeValue, ComponentType<RequestDetailsProps>>> = {
  REBOOT: RebootRequestDetails,
  TOPUP: TopupRequestDetails,
};
