import type { Tone } from "@/components/ui/badge";
import type {
  AgentInvoiceStatus,
  ClientInvoiceStatus,
  RequestStatus,
  SimStatus,
  SmartphoneStatus,
} from "@/lib/demo/types";
import type {
  ClientInvoiceStatusValue,
  RequestStatusValue,
  SimCardStatusValue,
  SmartphoneStatusValue,
} from "@/lib/api/types";

export const requestStatusTone: Record<RequestStatus, Tone> = {
  Submitted: "info",
  "In Progress": "warning",
  Completed: "success",
  Cancelled: "neutral",
};

export const smartphoneStatusTone: Record<SmartphoneStatus, Tone> = {
  Active: "success",
  "In Repair": "warning",
  Retired: "neutral",
};

export const simStatusTone: Record<SimStatus, Tone> = {
  Active: "success",
  Retired: "neutral",
};

// Same tones as smartphoneStatusTone/simStatusTone above, keyed by the real backend enum value
// instead of the demo data's human-readable label (fleet-management ticket: real Fleet views
// consume SmartphoneListItem/SimCardListItem, not demo/types).
export const smartphoneStatusToneByValue: Record<SmartphoneStatusValue, Tone> = {
  ACTIVE: "success",
  IN_REPAIR: "warning",
  RETIRED: "neutral",
};

export const simCardStatusToneByValue: Record<SimCardStatusValue, Tone> = {
  ACTIVE: "success",
  RETIRED: "neutral",
};

// Same tones as requestStatusTone above, keyed by the real backend enum value
// (tester-request-submission ticket: real Request views consume RequestListItem, not demo/types).
export const requestStatusToneByValue: Record<RequestStatusValue, Tone> = {
  SUBMITTED: "info",
  IN_PROGRESS: "warning",
  COMPLETED: "success",
  CANCELLED: "neutral",
};

export const clientInvoiceStatusTone: Record<ClientInvoiceStatus, Tone> = {
  draft: "neutral",
  sent: "warning",
  approved: "success",
};

export const clientInvoiceStatusLabel: Record<ClientInvoiceStatus, string> = {
  draft: "Draft",
  sent: "Awaiting approval",
  approved: "Approved",
};

// Same tones/labels as clientInvoiceStatusTone/clientInvoiceStatusLabel above, keyed by the real
// backend enum value instead of the demo data's lowercase label (client-invoice-generation
// ticket: the real Client Invoices view consumes ClientInvoiceDetail, not demo/types).
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

export const agentInvoiceStatusTone: Record<AgentInvoiceStatus, Tone> = {
  draft: "neutral",
  sent: "warning",
  approved: "info",
  paid: "success",
};

export const agentInvoiceStatusLabel: Record<AgentInvoiceStatus, string> = {
  draft: "Draft",
  sent: "Awaiting approval",
  approved: "Approved",
  paid: "Paid",
};
