import type { Tone } from "@/components/ui/badge";
import type {
  AgentInvoiceStatus,
  ClientInvoiceStatus,
  RequestStatus,
  SimStatus,
  SmartphoneStatus,
} from "@/lib/demo/types";

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
