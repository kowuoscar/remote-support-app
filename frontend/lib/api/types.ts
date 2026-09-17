/** Mirrors backend/.../domain/Country.java — a fixed six-country lookup that deterministically
 * fixes an Agent's currency (spec.md: "a country (which fixes their currency)"). */
export type Country = "FRANCE" | "SPAIN" | "UNITED_KINGDOM" | "MEXICO" | "PHILIPPINES" | "UNITED_STATES";

export const COUNTRIES: { value: Country; label: string; currency: string }[] = [
  { value: "FRANCE", label: "France", currency: "EUR" },
  { value: "SPAIN", label: "Spain", currency: "EUR" },
  { value: "UNITED_KINGDOM", label: "United Kingdom", currency: "GBP" },
  { value: "MEXICO", label: "Mexico", currency: "MXN" },
  { value: "PHILIPPINES", label: "Philippines", currency: "PHP" },
  { value: "UNITED_STATES", label: "United States", currency: "USD" },
];

export function countryLabel(country: string): string {
  return COUNTRIES.find((c) => c.value === country)?.label ?? country;
}

// Mirrors backend/.../dto/ClientResponse.java
export interface ClientListItem {
  id: string;
  name: string;
  primaryContactUsername: string | null;
  contractCount: number;
}

// Mirrors backend/.../dto/TesterResponse.java
export interface TesterListItem {
  id: string;
  clientId: string;
  username: string;
  isPrimaryContact: boolean;
}

// Mirrors backend/.../dto/AgentResponse.java
export interface AgentListItem {
  id: string;
  name: string;
  country: Country;
  currency: string;
  salaryAmount: number;
  contractCount: number;
}

// Mirrors backend/.../dto/ContractResponse.java
export interface ContractListItem {
  id: string;
  clientId: string;
  clientName: string;
  agentId: string;
  agentName: string;
  country: string;
  currency: string;
}

// Mirrors backend/.../domain/SmartphoneStatus.java
export type SmartphoneStatusValue = "ACTIVE" | "IN_REPAIR" | "RETIRED";

// Mirrors backend/.../domain/SimCardStatus.java
export type SimCardStatusValue = "ACTIVE" | "RETIRED";

// Mirrors backend/.../domain/SimCardFlavor.java
export type SimCardFlavorValue = "POSTPAID" | "PREPAID";

export const SMARTPHONE_STATUS_LABEL: Record<SmartphoneStatusValue, string> = {
  ACTIVE: "Active",
  IN_REPAIR: "In Repair",
  RETIRED: "Retired",
};

export const SIM_CARD_STATUS_LABEL: Record<SimCardStatusValue, string> = {
  ACTIVE: "Active",
  RETIRED: "Retired",
};

export const SIM_CARD_FLAVOR_LABEL: Record<SimCardFlavorValue, string> = {
  POSTPAID: "Postpaid",
  PREPAID: "Prepaid",
};

/** Mirrors backend/.../domain/SmartphoneStatus.java#canTransitionTo. */
export function nextSmartphoneStatuses(current: SmartphoneStatusValue): SmartphoneStatusValue[] {
  switch (current) {
    case "ACTIVE":
      return ["IN_REPAIR", "RETIRED"];
    case "IN_REPAIR":
      return ["ACTIVE", "RETIRED"];
    case "RETIRED":
      return [];
  }
}

// Mirrors backend/.../dto/SmartphoneResponse.java
export interface SmartphoneListItem {
  id: string;
  contractId: string;
  model: string;
  serial: string;
  assignedTo: string | null;
  status: SmartphoneStatusValue;
}

// Mirrors backend/.../dto/SimCardResponse.java
export interface SimCardListItem {
  id: string;
  contractId: string;
  number: string;
  carrier: string | null;
  flavor: SimCardFlavorValue;
  monthlyFeeAmount: number | null;
  status: SimCardStatusValue;
}

// Mirrors backend/.../domain/RequestType.java
export type RequestTypeValue =
  | "REBOOT"
  | "TOPUP"
  | "SIM_SWAP"
  | "PROVISION_SMARTPHONE"
  | "PROVISION_SIM"
  | "REPAIR";

// Mirrors backend/.../domain/RequestStatus.java.
export type RequestStatusValue = "SUBMITTED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";

/**
 * Mirrors backend/.../domain/RequestStatus.java#canTransitionTo — the single forward-progress
 * step an Agent can take next (agent-request-fulfillment ticket AC: "Agent can move a Request
 * from Submitted to In Progress, and from In Progress to Completed"), or `null` once terminal.
 * Cancelling is a separate action (needs a reason), not part of this forward path.
 */
export function nextRequestStatus(current: RequestStatusValue): RequestStatusValue | null {
  switch (current) {
    case "SUBMITTED":
      return "IN_PROGRESS";
    case "IN_PROGRESS":
      return "COMPLETED";
    case "COMPLETED":
    case "CANCELLED":
      return null;
  }
}

/** Whether a Request in this status can still be cancelled (mirrors RequestStatus#canTransitionTo). */
export function canCancelRequest(current: RequestStatusValue): boolean {
  return current === "SUBMITTED" || current === "IN_PROGRESS";
}

export const REQUEST_TYPE_LABEL: Record<RequestTypeValue, string> = {
  REBOOT: "Reboot",
  TOPUP: "Topup",
  SIM_SWAP: "SIM Swap",
  PROVISION_SMARTPHONE: "Provision Smartphone",
  PROVISION_SIM: "Provision SIM",
  REPAIR: "Repair",
};

export const REQUEST_STATUS_LABEL: Record<RequestStatusValue, string> = {
  SUBMITTED: "Submitted",
  IN_PROGRESS: "In Progress",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

// Mirrors backend/.../dto/RequestResponse.java
export interface RequestListItem {
  id: string;
  contractId: string;
  type: RequestTypeValue;
  status: RequestStatusValue;
  raisedByTesterId: string;
  raisedByUsername: string;
  // agent-request-fulfillment ticket: whether an Agent logged this Request proactively, on a
  // Tester's behalf, rather than the Tester submitting it themselves — and, when so, who actually
  // logged it (the Agent's own username, distinct from raisedByUsername, the Tester it's for).
  agentAuthored: boolean;
  loggedByUsername: string;
  cancellationReason: string | null;
  createdAt: string;
}

// Mirrors backend/.../dto/TesterResponse.java, as returned by GET /api/contracts/{id}/testers
// (agent-request-fulfillment ticket) — the picker an Agent uses to name whose behalf a
// proactively-logged Request is raised on.
export interface ContractTesterListItem {
  id: string;
  clientId: string;
  username: string;
  isPrimaryContact: boolean;
}

// Mirrors backend/.../domain/FeeType.java — the four RequestTypeValues that can carry a Fee
// (fee-logging-and-provisioning ticket). A Reboot or a like-for-like SIM Swap is never one of
// these; a swap that really needed a new physical SIM is logged as PROVISION_SIM instead.
export type FeeTypeValue = "TOPUP" | "PROVISION_SMARTPHONE" | "PROVISION_SIM" | "REPAIR";

export const FEE_TYPE_LABEL: Record<FeeTypeValue, string> = {
  TOPUP: "Topup",
  PROVISION_SMARTPHONE: "Provision Smartphone",
  PROVISION_SIM: "Provision SIM",
  REPAIR: "Repair",
};

/** Mirrors backend/.../domain/FeeType.java#requestTypeCanCarryFee. */
export function requestTypeCanCarryFee(type: RequestTypeValue): type is FeeTypeValue {
  return type === "TOPUP" || type === "PROVISION_SMARTPHONE" || type === "PROVISION_SIM" || type === "REPAIR";
}

// Mirrors backend/.../dto/FeeResponse.java
export interface FeeListItem {
  id: string;
  contractId: string;
  requestId: string;
  requestType: RequestTypeValue;
  feeType: FeeTypeValue;
  amount: number;
  currency: string;
  description: string | null;
  billingMonth: string;
  createdAt: string;
}

// Mirrors backend/.../domain/ClientInvoiceStatus.java. DRAFT is the only value this ticket
// (client-invoice-generation) ever writes — SENT/APPROVED are declared now so
// client-invoice-submission-and-visibility doesn't need a frontend type change either.
export type ClientInvoiceStatusValue = "DRAFT" | "SENT" | "APPROVED";

export const CLIENT_INVOICE_STATUS_LABEL: Record<ClientInvoiceStatusValue, string> = {
  DRAFT: "Draft",
  SENT: "Awaiting approval",
  APPROVED: "Approved",
};

// Mirrors backend/.../dto/CarrierInvoiceFileResponse.java
export interface CarrierInvoiceFileListItem {
  id: string;
  clientInvoiceId: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

// Mirrors backend/.../dto/ClientInvoiceResponse.java. While `status` is DRAFT,
// baseAmount/feeLines/totalAmount are computed live by the backend on every fetch
// (client-invoice-generation ticket); from SENT onward they are read from the frozen snapshot
// instead (client-invoice-submission-and-visibility ticket) — the shape is identical either way,
// only where the backend sourced the numbers changes. sentAt/approvedAt are null until each
// transition happens.
export interface ClientInvoiceDetail {
  id: string;
  contractId: string;
  billingMonth: string;
  status: ClientInvoiceStatusValue;
  currency: string;
  baseAmount: number;
  feeLines: FeeListItem[];
  totalAmount: number;
  files: CarrierInvoiceFileListItem[];
  sentAt: string | null;
  approvedAt: string | null;
}

// Mirrors backend/.../domain/StandingAmountType.java
export type StandingAmountTypeValue = "SALARY" | "ROLLOUT_ADVANCE";

// Mirrors backend/.../domain/AgentInvoiceStatus.java. agent-invoice-submission-and-approval
// ticket completes the lifecycle — agent-standing-amounts-and-invoice-generation only ever wrote
// DRAFT.
export type AgentInvoiceStatusValue = "DRAFT" | "SENT" | "APPROVED" | "PAID";

// Mirrors backend/.../dto/AgentInvoiceResponse.java. While `status` is DRAFT, every line is
// computed live by the backend on every fetch; from SENT onward they're read from the frozen
// snapshot instead (agent-invoice-submission-and-approval ticket; ADR 0003) — the shape is
// identical either way, mirroring ClientInvoiceDetail's own live/frozen split.
// rolloutAdvanceRepayment is negative-or-zero, rolloutAdvanceNewAdvance is positive-or-zero;
// totalAmount sums all four lines. sentAt/approvedAt/paidAt are null until each transition
// happens.
export interface AgentInvoiceDetail {
  id: string;
  agentId: string;
  billingMonth: string;
  status: AgentInvoiceStatusValue;
  currency: string;
  localSupportFees: number;
  salary: number;
  rolloutAdvanceRepayment: number;
  rolloutAdvanceNewAdvance: number;
  totalAmount: number;
  sentAt: string | null;
  approvedAt: string | null;
  paidAt: string | null;
}

// Mirrors backend/.../dto/AgentStandingAmountsResponse.java — the salary/Rollout Advance amounts
// currently in effect for an Agent (i.e. resolved for the calendar month in progress).
export interface AgentStandingAmounts {
  salaryAmount: number;
  rolloutAdvanceAmount: number;
}

// Mirrors backend/.../dto/AgentStandingAmountResponse.java — confirms what a Manager's standing-
// amount update scheduled, and from which month it takes effect.
export interface AgentStandingAmountChange {
  id: string;
  agentId: string;
  amountType: StandingAmountTypeValue;
  amount: number;
  effectiveMonth: string;
}

// Mirrors backend/.../dto/ReviewQueueItemResponse.java — one invoice waiting on a Manager action
// (CONTEXT.md "Review Queue"). totalAmount is always the invoice's frozen snapshot; waitingSince is
// when it entered the status it is waiting in (sentAt, or approvedAt for an approved Agent
// Invoice), and the backend orders the queue by it.
export type ReviewQueueKind = "CLIENT_INVOICE" | "AGENT_INVOICE";

interface ReviewQueueItemBase {
  id: string;
  billingMonth: string;
  agentName: string;
  currency: string;
  totalAmount: number;
  waitingSince: string;
}

export interface ClientInvoiceQueueItem extends ReviewQueueItemBase {
  kind: "CLIENT_INVOICE";
  status: ClientInvoiceStatusValue;
  contractId: string;
  clientName: string;
  agentId: null;
}

export interface AgentInvoiceQueueItem extends ReviewQueueItemBase {
  kind: "AGENT_INVOICE";
  status: AgentInvoiceStatusValue;
  contractId: null;
  clientName: null;
  agentId: string;
}

export type ReviewQueueItem = ClientInvoiceQueueItem | AgentInvoiceQueueItem;
