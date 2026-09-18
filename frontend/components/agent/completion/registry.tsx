import type { ComponentType } from "react";
import type { RequestTypeValue } from "@/lib/api/types";
import { ProvisionSimCompletion } from "./provision-sim-completion";
import { ProvisionSmartphoneCompletion } from "./provision-smartphone-completion";
import { ReplaceSimCompletion } from "./replace-sim-completion";
import { ReplaceSmartphoneCompletion } from "./replace-smartphone-completion";
import type { CompletionFormProps } from "./types";

/**
 * The one registry a later ticket extends to give another Request type its own completion-form
 * piece inside the Agent's completion step (provision-request-details ticket — mirrors
 * `components/requests/details/registry.tsx`'s shape exactly, so `replace-requests`,
 * `sim-swap-moves` and a later feature's Stock fulfilment each add one new file plus one line
 * here, never touching `RequestStatusControl`'s shared shell). A type with no entry here needs
 * nothing beyond the shell's own Fee amount/description fields — Reboot, Topup, SIM Swap and Other
 * as of this ticket.
 */
export const REQUEST_COMPLETION_COMPONENTS: Partial<Record<RequestTypeValue, ComponentType<CompletionFormProps>>> = {
  PROVISION_SMARTPHONE: ProvisionSmartphoneCompletion,
  PROVISION_SIM: ProvisionSimCompletion,
  REPLACE_SMARTPHONE: ReplaceSmartphoneCompletion,
  REPLACE_SIM: ReplaceSimCompletion,
};
