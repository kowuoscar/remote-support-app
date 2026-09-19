import type { RequestListItem } from "@/lib/api/types";

/**
 * The shared props every per-type approval-payload piece receives inside
 * `PendingRequestDecisionControls`' approve form (review finding on this feature's finisher pass —
 * mirrors `components/agent/completion/types.ts`'s `CompletionFormProps` shape at the other end of
 * a Request's life: a type-specific piece owns its own fields, the generic control only renders
 * whichever one is registered for `request.type` and never knows what it looks like).
 */
export interface ApprovalPayloadProps {
  request: RequestListItem;
  disabled?: boolean;
}

/**
 * Turns a completed approve form's `FormData` into the JSON body sent to `POST
 * /api/requests/{id}/approve` (mirrors `components/agent/completion/types.ts`'s
 * `CompletionBodyBuilder`). Only called when the type's own `needsApprovalForm` predicate says the
 * approve control rendered a `<form>` at all — a type that approves with a single click, no body,
 * never needs one.
 */
export type ApprovalBodyBuilder = (request: RequestListItem, formData: FormData) => Record<string, unknown>;
