import type { ComponentType } from "react";
import type { RequestListItem, RequestTypeValue } from "@/lib/api/types";
import { buildProvisionSimCompletionBody, ProvisionSimCompletion } from "./provision-sim-completion";
import { buildProvisionSmartphoneCompletionBody, ProvisionSmartphoneCompletion } from "./provision-smartphone-completion";
import { buildReplaceSimCompletionBody, ReplaceSimCompletion } from "./replace-sim-completion";
import { buildReplaceSmartphoneCompletionBody, ReplaceSmartphoneCompletion } from "./replace-smartphone-completion";
import { buildReturnCompletionBody, returnCompletionNeedsOwnForm, ReturnCompletion } from "./return-completion";
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
  RETURN: ReturnCompletion,
};

/**
 * The matching registry for how each type's completion piece turns its own form fields into the
 * type-specific part of the completion PATCH body (code review finding: this used to be an
 * `if/else if` cascade in `request-status-control.tsx`'s `confirmComplete`, kept in lockstep with
 * `REQUEST_COMPLETION_COMPONENTS` by hand). A type with no entry — Reboot, Topup, SIM Swap and
 * Other — sends nothing beyond the shell's own base `{ status: "COMPLETED" }` body. Replace
 * Smartphone gained an entry in fulfil-from-stock (it can now send
 * `fulfillFromStockSmartphoneId`) even though it still needs no Agent input otherwise.
 */
export const REQUEST_COMPLETION_BODY_BUILDERS: Partial<Record<RequestTypeValue, CompletionBodyBuilder>> = {
  PROVISION_SMARTPHONE: buildProvisionSmartphoneCompletionBody,
  PROVISION_SIM: buildProvisionSimCompletionBody,
  REPLACE_SMARTPHONE: buildReplaceSmartphoneCompletionBody,
  REPLACE_SIM: buildReplaceSimCompletionBody,
  RETURN: buildReturnCompletionBody,
};

/**
 * A Return can never carry a Fee (`requestTypeCanCarryFee` is `false` for it), so it's the first
 * type whose completing form has to open for a reason other than "may need to log a Fee" —
 * cancelling one or more SIM Cards needs their effective dates (manager-decides-return-disposition
 * ticket). A type with no entry here needs its own form to open only when it can carry a Fee, as
 * before this ticket.
 */
export const REQUEST_COMPLETION_NEEDS_OWN_FORM: Partial<Record<RequestTypeValue, (request: RequestListItem) => boolean>> = {
  RETURN: returnCompletionNeedsOwnForm,
};
