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

// Mirrors backend/.../dto/CarrierResponse.java — archivedAt is null while the Carrier is active.
export interface CarrierItem {
  id: string;
  country: Country;
  name: string;
  archivedAt: string | null;
}

// Mirrors backend/.../dto/CarrierOfferResponse.java — a Topup Option or a Postpaid Plan. `price`
// is in the catalog's currency; archivedAt is null while the entry is active.
export interface CarrierOfferItem {
  id: string;
  carrierId: string;
  name: string;
  price: number;
  archivedAt: string | null;
}

// Mirrors backend/.../dto/CatalogCarrierResponse.java — a Carrier as the catalog lists it, with
// its Topup Options and Postpaid Plans (active first, then cheapest first).
export interface CatalogCarrierItem extends CarrierItem {
  topupOptions: CarrierOfferItem[];
  postpaidPlans: CarrierOfferItem[];
}

// Mirrors backend/.../dto/CarrierCatalogResponse.java — one Country's Carriers, active first,
// with the currency every price in that catalog is in.
export interface CarrierCatalog {
  country: Country;
  currency: string;
  carriers: CatalogCarrierItem[];
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
  // The email the Agent signs in with; null for an Agent that has no login yet.
  loginUsername: string | null;
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

// Mirrors backend/.../domain/SmartphoneOwner.java
export type SmartphoneOwnerValue = "CLIENT" | "COMPANY";

export const SMARTPHONE_OWNER_LABEL: Record<SmartphoneOwnerValue, string> = {
  CLIENT: "Client",
  COMPANY: "Company",
};

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

// Mirrors backend/.../dto/SmartphoneResponse.java. serial is null for a Smartphone created
// without one (smartphone-owner-and-optional-serial ticket) until set later from the Fleet page.
export interface SmartphoneListItem {
  id: string;
  contractId: string;
  model: string;
  serial: string | null;
  owner: SmartphoneOwnerValue;
  status: SmartphoneStatusValue;
}

// Mirrors backend/.../dto/SimCardResponse.java. The three carrier fields are absent for a SIM Card
// from before the Carrier catalog that never had a carrier, and the three plan fields for a Prepaid
// SIM or a Postpaid SIM from before the catalog, which keeps its own monthly fee.
// installedInSmartphoneId/Model are absent when the SIM Card isn't Installed in any Smartphone
// (spec.md Solution — Fleet model: "Installed in"; sim-installed-in-smartphone ticket).
export interface SimCardListItem {
  id: string;
  contractId: string;
  number: string;
  carrierId?: string;
  carrierName?: string;
  carrierArchived?: boolean;
  postpaidPlanId?: string;
  postpaidPlanName?: string;
  postpaidPlanArchived?: boolean;
  flavor: SimCardFlavorValue;
  monthlyFeeAmount: number | null;
  status: SimCardStatusValue;
  installedInSmartphoneId?: string;
  installedInSmartphoneModel?: string;
  // manager-decides-return-disposition ticket: set only for a SIM Card retired through a
  // cancelled Return, keeping the effective cancellation date it was given at completion.
  cancellationEffectiveDate?: string;
}

// Mirrors backend/.../domain/RequestType.java. OTHER replaces the former REPAIR
// (other-replaces-repair ticket) — a free-text Request for support no other type covers.
// REPLACE_SMARTPHONE/REPLACE_SIM (replace-requests ticket) swap out one named Fleet unit for a
// new one — distinct from PROVISION_SMARTPHONE/PROVISION_SIM, which only ever mean a net-new unit.
// RETURN (returns-and-agent-stock spec; return-client-owned-smartphones ticket) names one or more
// Smartphones/SIM Cards leaving a Contract's Fleet, each with its own Disposition.
export type RequestTypeValue =
  | "REBOOT"
  | "TOPUP"
  | "SIM_SWAP"
  | "PROVISION_SMARTPHONE"
  | "PROVISION_SIM"
  | "REPLACE_SMARTPHONE"
  | "REPLACE_SIM"
  | "OTHER"
  | "RETURN";

// Mirrors backend/.../domain/Disposition.java (returns-and-agent-stock spec; CONTEXT.md
// "Disposition"). This ticket (return-client-owned-smartphones) only ever produces
// POSTED_TO_CLIENT, fixed at submission; the other three are chosen by the Company Manager at
// approval (manager-decides-return-disposition, agent-stock tickets) — declared here now, the
// same way RequestStatusValue declared PENDING_APPROVAL/REJECTED ahead of the ticket that first
// wrote them.
export type DispositionValue = "POSTED_TO_CLIENT" | "POSTED_TO_COMPANY" | "CANCELLED" | "KEPT_IN_STOCK";

export const DISPOSITION_LABEL: Record<DispositionValue, string> = {
  POSTED_TO_CLIENT: "Posted to Client",
  POSTED_TO_COMPANY: "Posted to company",
  CANCELLED: "Cancelled",
  KEPT_IN_STOCK: "Kept in Stock",
};

// Mirrors backend/.../dto/ReturnedUnitResponse.java — one unit named on a RETURN Request, with
// its own Disposition once decided. Exactly one of smartphoneId/simCardId is present.
export interface ReturnedUnitItem {
  id: string;
  smartphoneId?: string;
  smartphoneModel?: string;
  simCardId?: string;
  simCardNumber?: string;
  // agent-stock ticket: present only for a SIM Card unit, so the Manager's Disposition picker can
  // show a reminder on a Postpaid one without a second round-trip to the SIM Card itself.
  simCardFlavor?: SimCardFlavorValue;
  disposition?: DispositionValue;
}

// Mirrors backend/.../dto/StockUnitResponse.java (returns-and-agent-stock spec, Solution's Agent
// Stock; agent-stock ticket): one Smartphone or SIM Card in an Agent's Stock. Exactly one of the
// Smartphone-only fields (model, serial) or the SIM-Card-only fields (number, carrier*, flavor,
// postpaidPlan*, monthlyFeeAmount) is present, matching SmartphoneListItem's/SimCardListItem's own
// shapes — `kind` says which. fromContractId/fromClientName name the Contract the unit left.
export type StockUnitKind = "SMARTPHONE" | "SIM_CARD";

export interface StockUnitItem {
  id: string;
  kind: StockUnitKind;
  agentId: string;
  agentName: string;
  agentCurrency: string;
  model?: string;
  serial?: string;
  number?: string;
  carrierId?: string;
  carrierName?: string;
  carrierArchived?: boolean;
  flavor?: SimCardFlavorValue;
  postpaidPlanId?: string;
  postpaidPlanName?: string;
  postpaidPlanArchived?: boolean;
  monthlyFeeAmount?: number;
  status: string;
  fromContractId?: string;
  fromClientName?: string;
}

// Mirrors backend/.../domain/RequestStatus.java. PENDING_APPROVAL/REJECTED
// (manager-approves-requests ticket): the starting status of every approval-required type, and the
// Manager's "no" — see requiresApproval below.
export type RequestStatusValue =
  | "PENDING_APPROVAL"
  | "SUBMITTED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED"
  | "REJECTED";

/**
 * Mirrors backend/.../domain/RequestStatus.java#canTransitionTo — the single forward-progress
 * step an Agent can take next (agent-request-fulfillment ticket AC: "Agent can move a Request
 * from Submitted to In Progress, and from In Progress to Completed"), or `null` once terminal or
 * once only the Manager's own approve/reject actions move it on (manager-approves-requests
 * ticket: Pending Approval has no "next" an Agent can reach this way). Cancelling is a separate
 * action (needs a reason), not part of this forward path.
 */
export function nextRequestStatus(current: RequestStatusValue): RequestStatusValue | null {
  switch (current) {
    case "SUBMITTED":
      return "IN_PROGRESS";
    case "IN_PROGRESS":
      return "COMPLETED";
    case "PENDING_APPROVAL":
    case "COMPLETED":
    case "CANCELLED":
    case "REJECTED":
      return null;
  }
}

/** Whether a Request in this status can still be cancelled (mirrors RequestStatus#canTransitionTo). */
export function canCancelRequest(current: RequestStatusValue): boolean {
  return current === "PENDING_APPROVAL" || current === "SUBMITTED" || current === "IN_PROGRESS";
}

export const REQUEST_TYPE_LABEL: Record<RequestTypeValue, string> = {
  REBOOT: "Reboot",
  TOPUP: "Topup",
  SIM_SWAP: "SIM Swap",
  PROVISION_SMARTPHONE: "Provision Smartphone",
  PROVISION_SIM: "Provision SIM",
  REPLACE_SMARTPHONE: "Replace Smartphone",
  REPLACE_SIM: "Replace SIM",
  OTHER: "Other",
  RETURN: "Return",
};

export const REQUEST_STATUS_LABEL: Record<RequestStatusValue, string> = {
  PENDING_APPROVAL: "Pending Approval",
  SUBMITTED: "Submitted",
  IN_PROGRESS: "In Progress",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
  REJECTED: "Rejected",
};

// Mirrors backend/.../domain/RequestType.java#requiresApproval (manager-approves-requests
// ticket): the four types that always start Pending Approval, whoever raises them.
export function requestTypeRequiresApproval(type: RequestTypeValue): boolean {
  return (
    type === "PROVISION_SMARTPHONE" ||
    type === "PROVISION_SIM" ||
    type === "REPLACE_SMARTPHONE" ||
    type === "REPLACE_SIM"
  );
}

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
  // manager-approves-requests ticket: set only once a Manager has decided (spec.md Manager
  // approval: "the decision records who decided and when"); rejectionReason only on a REJECTED
  // one, shown the same way cancellationReason is (CONTEXT.md "Rejected" — distinct from Cancelled).
  rejectionReason?: string;
  decidedByUsername?: string;
  decidedAt?: string;
  // Free-text detail given at submission (other-replaces-repair ticket): optional for every type
  // except OTHER, where it's required.
  description: string | null;
  createdAt: string;
  // reboot-and-topup-details ticket: the target unit a Reboot/Topup Request names, and the Topup
  // Option it asked for, denormalized so a Requests list row never needs a second round-trip.
  // Absent for every other type, and for a Request that existed before this ticket.
  targetSmartphoneId?: string;
  targetSmartphoneModel?: string;
  targetSimCardId?: string;
  targetSimCardNumber?: string;
  topupOptionId?: string;
  topupOptionName?: string;
  // The Option's price, for the Agent's completion step to pre-fill the Fee amount from
  // (still editable) — the amount itself is never re-read from the Option after that.
  topupOptionPrice?: number;
  // provision-request-details ticket: a Provision Smartphone Request's own requested model, and a
  // Provision SIM Request's flavor, Carrier and, for postpaid, Postpaid Plan — denormalized the
  // same way as the target/Topup fields above. A Provision SIM's optional target Smartphone
  // reuses targetSmartphoneId/targetSmartphoneModel above. Absent for every other type, and for a
  // Request that existed before this ticket.
  requestedModel?: string;
  requestedFlavor?: SimCardFlavorValue;
  requestedCarrierId?: string;
  requestedCarrierName?: string;
  requestedCarrierArchived?: boolean;
  requestedPostpaidPlanId?: string;
  requestedPostpaidPlanName?: string;
  requestedPostpaidPlanArchived?: boolean;
  // A one-time note from completing this Request (e.g. "added uninstalled, no room") — only ever
  // present on the single response a completion PATCH itself returns.
  completionNote?: string;
  // sim-swap-moves ticket: a SIM Swap exchange's second move — targetSimCardId/targetSmartphoneId
  // above double as its first. Absent for a plain single move, every other type, and a SIM Swap
  // Request that existed before this ticket.
  secondSimCardId?: string;
  secondSimCardNumber?: string;
  secondTargetSmartphoneId?: string;
  secondTargetSmartphoneModel?: string;
  // return-client-owned-smartphones ticket: every unit a RETURN Request names, with its own
  // Disposition once decided. Absent for every other type.
  returnedUnits?: ReturnedUnitItem[];
}

// Mirrors backend/.../dto/PendingRequestItemResponse.java, as returned by GET
// /api/pending-requests (manager-approves-requests ticket): one Request at Pending Approval, for
// the Manager's Pending Requests page — request carries the type, Tester, Agent-authored flag and
// every type's own denormalized details (for a Replace, the unit that would be retired);
// clientName/agentName/waitingSince are this list's own addition on top.
export interface PendingRequestItem {
  request: RequestListItem;
  clientName: string;
  agentName: string;
  waitingSince: string;
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

// Mirrors backend/.../domain/FeeType.java — the six RequestTypeValues that can carry a Fee
// (fee-logging-and-provisioning, other-replaces-repair, replace-requests tickets). A Reboot or a
// like-for-like SIM Swap is never one of these; a swap that really needed a new physical SIM is
// logged as PROVISION_SIM instead.
export type FeeTypeValue =
  | "TOPUP"
  | "PROVISION_SMARTPHONE"
  | "PROVISION_SIM"
  | "REPLACE_SMARTPHONE"
  | "REPLACE_SIM"
  | "OTHER";

export const FEE_TYPE_LABEL: Record<FeeTypeValue, string> = {
  TOPUP: "Topup",
  PROVISION_SMARTPHONE: "Provision Smartphone",
  PROVISION_SIM: "Provision SIM",
  REPLACE_SMARTPHONE: "Replace Smartphone",
  REPLACE_SIM: "Replace SIM",
  OTHER: "Other",
};

/** Mirrors backend/.../domain/FeeType.java#requestTypeCanCarryFee. */
export function requestTypeCanCarryFee(type: RequestTypeValue): type is FeeTypeValue {
  return (
    type === "TOPUP" ||
    type === "PROVISION_SMARTPHONE" ||
    type === "PROVISION_SIM" ||
    type === "REPLACE_SMARTPHONE" ||
    type === "REPLACE_SIM" ||
    type === "OTHER"
  );
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
  // The Topup Option a Topup Fee was bought from; absent when it names none.
  topupOptionId?: string;
  topupOptionName?: string;
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
