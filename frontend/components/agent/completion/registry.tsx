import type { ComponentType } from "react";
import type { RequestTypeValue } from "@/lib/api/types";
import { buildProvisionSimCompletionBody, ProvisionSimCompletion } from "./provision-sim-completion";
import { buildProvisionSmartphoneCompletionBody, ProvisionSmartphoneCompletion } from "./provision-smartphone-completion";
import { buildReplaceSimCompletionBody, ReplaceSimCompletion } from "./replace-sim-completion";
import { ReplaceSmartphoneCompletion } from "./replace-smartphone-completion";
import type { CompletionBodyBuilder, CompletionFormProps } from "./types";

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

/**
 * The matching registry for how each type's completion piece turns its own form fields into the
 * type-specific part of the completion PATCH body (code review finding: this used to be an
 * `if/else if` cascade in `request-status-control.tsx`'s `confirmComplete`, kept in lockstep with
 * `REQUEST_COMPLETION_COMPONENTS` by hand). A type with no entry — Reboot, Topup, SIM Swap, Other,
 * and Replace Smartphone (no Agent input at all) — sends nothing beyond the shell's own base
 * `{ status: "COMPLETED" }` body.
 */
export const REQUEST_COMPLETION_BODY_BUILDERS: Partial<Record<RequestTypeValue, CompletionBodyBuilder>> = {
  PROVISION_SMARTPHONE: buildProvisionSmartphoneCompletionBody,
  PROVISION_SIM: buildProvisionSimCompletionBody,
  REPLACE_SIM: buildReplaceSimCompletionBody,
};
