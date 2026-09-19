import type { ComponentType } from "react";
import type { RequestListItem, RequestTypeValue } from "@/lib/api/types";
import { buildReturnApprovalBody, returnApprovalNeedsForm, ReturnApprovalPayload } from "./return-approval-payload";
import type { ApprovalBodyBuilder, ApprovalPayloadProps } from "./types";

/**
 * The one registry a later type extends to give its own approval-payload piece inside the
 * Manager's Pending Requests approve control (review finding on this feature's finisher pass —
 * mirrors `components/agent/completion/registry.tsx`'s shape exactly). A type with no entry here
 * approves with no extra payload: one click, no body — every type but `RETURN` as of this pass.
 */
export const REQUEST_APPROVAL_PAYLOAD_COMPONENTS: Partial<Record<RequestTypeValue, ComponentType<ApprovalPayloadProps>>> = {
  RETURN: ReturnApprovalPayload,
};

/** The matching registry for how each type's payload piece turns its own form fields into the approve body. */
export const REQUEST_APPROVAL_BODY_BUILDERS: Partial<Record<RequestTypeValue, ApprovalBodyBuilder>> = {
  RETURN: buildReturnApprovalBody,
};

/**
 * Whether a Request of this type/content needs its own form to approve at all (a Return with
 * nothing left to choose approves with a single click, same as every type with no entry here).
 */
export const REQUEST_APPROVAL_NEEDS_FORM: Partial<Record<RequestTypeValue, (request: RequestListItem) => boolean>> = {
  RETURN: returnApprovalNeedsForm,
};
