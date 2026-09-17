/**
 * Which Agent Invoice a Manager action targets: by its own id (the Agent Invoice detail page, any
 * billing month), or as an Agent's current-month invoice (the Agent page, until
 * invoice-summaries-on-contract-and-agent-pages moves that review to the detail page).
 */
export type AgentInvoiceTarget = { invoiceId: string; agentId?: never } | { agentId: string; invoiceId?: never };

export function agentInvoiceActionUrl(target: AgentInvoiceTarget, action: "override" | "approve" | "paid"): string {
  return target.invoiceId !== undefined
    ? `/api/agent-invoices/${target.invoiceId}/${action}`
    : `/api/agents/${target.agentId}/invoice/${action}`;
}
