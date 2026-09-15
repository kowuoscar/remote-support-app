export type Currency = "USD" | "EUR" | "GBP" | "MXN" | "PHP";

export type RequestType =
  | "Reboot"
  | "Topup"
  | "SIM Swap"
  | "Provision Smartphone"
  | "Provision SIM"
  | "Repair";

export type RequestStatus = "Submitted" | "In Progress" | "Completed" | "Cancelled";

export type SmartphoneStatus = "Active" | "In Repair" | "Retired";
export type SimStatus = "Active" | "Retired";
export type SimFlavor = "Postpaid" | "Prepaid";

export type ClientInvoiceStatus = "draft" | "sent" | "approved";
export type AgentInvoiceStatus = "draft" | "sent" | "approved" | "paid";

export interface Contract {
  id: string;
  clientName: string;
  agentName: string;
  country: string;
  currency: Currency;
  label: string;
}

export interface ContractRef {
  contractId: string;
  contractLabel: string;
}

export interface RequestRecord extends ContractRef {
  id: string;
  type: RequestType;
  status: RequestStatus;
  raisedBy: string;
  assignedAgent?: string;
  createdAt: string;
  updatedAt: string;
  note?: string;
}

export interface Smartphone extends ContractRef {
  id: string;
  model: string;
  serial: string;
  status: SmartphoneStatus;
  assignedTo: string;
}

export interface SimCard extends ContractRef {
  id: string;
  number: string;
  flavor: SimFlavor;
  status: SimStatus;
  monthlyFee: number;
  carrier: string;
}

export interface ClientInvoiceRecord extends ContractRef {
  id: string;
  month: string;
  status: ClientInvoiceStatus;
  baseAmount: number;
  feesAmount: number;
  totalAmount: number;
  currency: Currency;
  carrierFilesAttached: number;
  sentAt?: string;
}

export interface AgentInvoiceRecord {
  id: string;
  agentName: string;
  month: string;
  status: AgentInvoiceStatus;
  localSupportFees: number;
  salary: number;
  rolloutRepayment: number;
  rolloutNewAdvance: number;
  currency: Currency;
  total: number;
  submittedAt?: string;
}
