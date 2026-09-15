import { agents, clients, contracts } from "./contracts";
import type { AgentInvoiceRecord, ClientInvoiceRecord } from "./types";

export { agents, clients, contracts };

export const pendingClientInvoices: ClientInvoiceRecord[] = [
  {
    id: "ci-2001",
    contractId: "ctr-aurora-fr",
    contractLabel: "Aurora Retail Group — France",
    month: "August 2026",
    status: "sent",
    baseAmount: 1840,
    feesAmount: 612.5,
    totalAmount: 2452.5,
    currency: "EUR",
    carrierFilesAttached: 2,
    sentAt: "2026-09-03T09:14:00Z",
  },
  {
    id: "ci-2002",
    contractId: "ctr-meridian-mx",
    contractLabel: "Meridian Logistics — Mexico",
    month: "August 2026",
    status: "sent",
    baseAmount: 2260,
    feesAmount: 940,
    totalAmount: 3200,
    currency: "MXN",
    carrierFilesAttached: 1,
    sentAt: "2026-09-04T13:40:00Z",
  },
  {
    id: "ci-2003",
    contractId: "ctr-brightpath-ph",
    contractLabel: "Bright Path Clinics — Philippines",
    month: "August 2026",
    status: "sent",
    baseAmount: 980,
    feesAmount: 175,
    totalAmount: 1155,
    currency: "PHP",
    carrierFilesAttached: 3,
    sentAt: "2026-09-06T08:02:00Z",
  },
];

export const pendingAgentInvoices: AgentInvoiceRecord[] = [
  {
    id: "ai-3001",
    agentName: "Camille Duforet",
    month: "August 2026",
    status: "sent",
    localSupportFees: 3120.5,
    salary: 2400,
    rolloutRepayment: -300,
    rolloutNewAdvance: 300,
    currency: "EUR",
    total: 5520.5,
    submittedAt: "2026-09-05T10:22:00Z",
  },
  {
    id: "ai-3002",
    agentName: "Priya Nair",
    month: "August 2026",
    status: "sent",
    localSupportFees: 1155,
    salary: 1450,
    rolloutRepayment: 0,
    rolloutNewAdvance: 0,
    currency: "PHP",
    total: 2605,
    submittedAt: "2026-09-06T15:47:00Z",
  },
];

export const pendingApprovals = [
  ...pendingClientInvoices.map((inv) => ({
    kind: "Client Invoice" as const,
    id: inv.id,
    subject: inv.contractLabel,
    month: inv.month,
    amount: inv.totalAmount,
    currency: inv.currency,
    submittedAt: inv.sentAt!,
  })),
  ...pendingAgentInvoices.map((inv) => ({
    kind: "Agent Invoice" as const,
    id: inv.id,
    subject: inv.agentName,
    month: inv.month,
    amount: inv.total,
    currency: inv.currency,
    submittedAt: inv.submittedAt!,
  })),
].sort((a, b) => new Date(a.submittedAt).getTime() - new Date(b.submittedAt).getTime());

export const tenantStats = {
  billedThisMonthUSD: 41280,
  payoutThisMonthUSD: 22940,
  clientCount: clients.length,
  agentCount: agents.length,
  contractCount: contracts.length,
};
