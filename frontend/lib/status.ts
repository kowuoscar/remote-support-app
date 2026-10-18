import type { Tone } from "@/components/ui/badge";
import type {
  AgentInvoiceStatusValue as RealAgentInvoiceStatusValue,
  ClientInvoiceStatusValue,
  RequestStatusValue,
  ReviewStatusValue,
  SimCardStatusValue,
  SmartphoneStatusValue,
} from "@/lib/api/types";

// Smartphone and SIM card badge tones, keyed by the real backend enum value
// (fleet-management ticket: Fleet views consume SmartphoneListItem/SimCardListItem).
export const smartphoneStatusToneByValue: Record<SmartphoneStatusValue, Tone> = {
  ACTIVE: "success",
  IN_REPAIR: "warning",
  RETIRED: "neutral",
};

export const simCardStatusToneByValue: Record<SimCardStatusValue, Tone> = {
  ACTIVE: "success",
  RETIRED: "neutral",
};

// Request badge tones, keyed by the real backend enum value
// (tester-request-submission ticket: Request views consume RequestListItem).
// PENDING_APPROVAL uses the warning tone and REJECTED the danger tone (spec.md Design direction;
// manager-approves-requests ticket) — the same two tones DESIGN.md's semantic list already names.
export const requestStatusToneByValue: Record<RequestStatusValue, Tone> = {
  PENDING_APPROVAL: "warning",
  SUBMITTED: "info",
  IN_PROGRESS: "warning",
  COMPLETED: "success",
  CANCELLED: "neutral",
  REJECTED: "danger",
};

// Client Invoice badge tones/labels, keyed by the real backend enum value
// (client-invoice-generation ticket: the Client Invoices view consumes ClientInvoiceDetail).
export const clientInvoiceStatusToneByValue: Record<ClientInvoiceStatusValue, Tone> = {
  DRAFT: "neutral",
  SENT: "warning",
  APPROVED: "success",
};

export const clientInvoiceStatusLabelByValue: Record<ClientInvoiceStatusValue, string> = {
  DRAFT: "Draft",
  SENT: "Awaiting approval",
  APPROVED: "Approved",
};

/** The badge of a draft the Manager sent back: derived display state, one label and tone everywhere. */
export const sentBackBadge: { label: string; tone: Tone } = { label: "Sent back", tone: "warning" };

export function isSentBackBadge(badge: { label: string } | null | undefined): boolean {
  return badge?.label === sentBackBadge.label;
}

/**
 * What a Client Invoice's status badge shows. A draft the Manager sent back is still `DRAFT` in the
 * backend; "Sent back" is a derived display state (send-a-client-invoice-back spec, Badge), so it
 * lives here with the other badge maps rather than in a view.
 */
export function clientInvoiceBadge(invoice: { status: ClientInvoiceStatusValue; sentBackAt?: string | null }): {
  label: string;
  tone: Tone;
} {
  if (invoice.status === "DRAFT" && invoice.sentBackAt) {
    return sentBackBadge;
  }
  return {
    label: clientInvoiceStatusLabelByValue[invoice.status],
    tone: clientInvoiceStatusToneByValue[invoice.status],
  };
}

// Agent Invoice badge tones/labels, keyed by the real backend enum value
// (agent-standing-amounts-and-invoice-generation ticket: the Agent Invoice view consumes
// AgentInvoiceDetail).
export const agentInvoiceStatusToneByValue: Record<RealAgentInvoiceStatusValue, Tone> = {
  DRAFT: "neutral",
  SENT: "warning",
  APPROVED: "info",
  PAID: "success",
};

export const agentInvoiceStatusLabelByValue: Record<RealAgentInvoiceStatusValue, string> = {
  DRAFT: "Draft",
  SENT: "Awaiting approval",
  APPROVED: "Approved",
  PAID: "Paid",
};

// Manager review status of a Client Invoice in an Agent's month (manager-reviews-by-agent spec):
// derived from the invoice's status, so "Sent back" is the one shared badge.
export const reviewStatusToneByValue: Record<ReviewStatusValue, Tone> = {
  TO_REVIEW: "warning",
  APPROVED: "success",
  SENT_BACK: sentBackBadge.tone,
};

export const reviewStatusLabelByValue: Record<ReviewStatusValue, string> = {
  TO_REVIEW: "To review",
  APPROVED: "Approved",
  SENT_BACK: sentBackBadge.label,
};
