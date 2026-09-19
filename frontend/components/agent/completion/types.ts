import type { CatalogCarrierItem, RequestListItem, SimCardListItem, SmartphoneListItem } from "@/lib/api/types";

/**
 * The shared props every per-type completion-form piece receives (provision-request-details
 * ticket) — mirrors `components/requests/details/types.ts`'s `RequestDetailsProps` shape at the
 * other end of a Request's life: {@link RequestListItem} carries whatever that type's own details
 * put on the Request at submission (e.g. `requestedModel`), which is what tells a piece whether to
 * render its new, narrow form or fall back to the legacy full one (ticket AC: "A Provision Request
 * created before this ticket completes through the previous full form"). Every field a piece's own
 * inputs need is a plain named form field (`name="..."`) inside `RequestStatusControl`'s single
 * surrounding `<form>`, exactly like the submission-time details pieces — no state is lifted back
 * up to the parent.
 */
export interface CompletionFormProps {
  request: RequestListItem;
  carriers: CatalogCarrierItem[];
  carriersHref: string;
  currency: string;
  activeSmartphones: SmartphoneListItem[];
  activeSimCards: SimCardListItem[];
  disabled?: boolean;
}

/**
 * Turns a completed completion form's `FormData` into the type-specific part of the completion
 * PATCH body (code review finding on `request-status-control.tsx`: payload-building used to live
 * in a per-type `if/else if` cascade in the shell even though rendering already went through
 * {@link CompletionFormProps}'s registry — moved here so each completion piece owns both its own
 * fields *and* how they become the request body; a new type adds a file and a registry entry and
 * never edits `RequestStatusControl`). Only the fields this type's own form renders are read off
 * `formData`; the shell's own shared fields (Fee `amount`/`description`, `status`) are never this
 * function's concern. Returns the fields to merge into the base `{ status: "COMPLETED" }` body —
 * `{}` when the type needs nothing beyond that.
 */
export type CompletionBodyBuilder = (request: RequestListItem, formData: FormData) => Record<string, unknown>;
