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
