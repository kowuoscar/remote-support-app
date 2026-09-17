import { notFound } from "next/navigation";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { Breadcrumb } from "@/components/app-shell/top-bar";
import { Card } from "@/components/ui/card";
import { AgentStandingAmountsView } from "@/components/manager/agent-standing-amounts-view";
import { InvoiceSummaryCard } from "@/components/manager/invoice-summary-card";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import { countryLabel, type AgentInvoiceDetail, type AgentListItem, type AgentStandingAmounts } from "@/lib/api/types";

export const metadata = { title: "Agent" };

/**
 * An Agent's detail view, added for the Manager to review the Agent and set their standing
 * salary/Rollout Advance (spec.md user stories 5-6; agent-standing-amounts-and-invoice-generation
 * ticket). There's no single-Agent GET endpoint, so (like `/manager/clients/[clientId]` and
 * `/manager/contracts/[contractId]`) this finds the Agent in the full list. The current-month Agent
 * Invoice shows only as a summary linking to the invoice's detail page, where the Manager
 * overrides, approves and marks it paid (manager-invoice-review-queue spec, Contract and Agent
 * pages). Its read keeps `GET /api/agents/{agentId}/invoice`'s get-or-create semantics, so there's
 * always a current-month invoice to link to.
 */
export default async function ManagerAgentDetailPage({
  params,
}: {
  params: Promise<{ agentId: string }>;
}) {
  await requireManager();
  const { agentId } = await params;

  const [agents, standingAmountsResponse, invoiceResponse] = await Promise.all([
    backendFetchList<AgentListItem>("/api/agents"),
    backendFetch(`/api/agents/${agentId}/standing-amounts`),
    backendFetch(`/api/agents/${agentId}/invoice`),
  ]);

  const agent = agents.find((a) => a.id === agentId);
  if (!agent) {
    notFound();
  }

  const standingAmounts: AgentStandingAmounts | null = standingAmountsResponse.ok
    ? ((await standingAmountsResponse.json()) as AgentStandingAmounts)
    : null;
  const invoice: AgentInvoiceDetail | null = invoiceResponse.ok
    ? ((await invoiceResponse.json()) as AgentInvoiceDetail)
    : null;

  return (
    <SurfacePage
      title={agent.name}
      subtitle={`${countryLabel(agent.country)} · ${agent.currency} · ${agent.contractCount} contract${agent.contractCount === 1 ? "" : "s"}`}
      viewerLabel="Manager"
    >
      <Breadcrumb items={[{ label: "Agents", href: "/manager/agents" }, { label: agent.name }]} />
      <div className="flex flex-col gap-5">
        <Card className="flex flex-wrap gap-6 p-5">
          <div>
            <p className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Country</p>
            <p className="mt-1 text-sm text-ink">{countryLabel(agent.country)}</p>
          </div>
          <div>
            <p className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Currency</p>
            <p className="mt-1 text-sm text-ink">{agent.currency}</p>
          </div>
          <div>
            <p className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Contracts</p>
            <p className="mt-1 text-sm text-ink">{agent.contractCount}</p>
          </div>
        </Card>

        {standingAmounts ? (
          <AgentStandingAmountsView
            agentId={agent.id}
            currency={agent.currency}
            salaryAmount={standingAmounts.salaryAmount}
            rolloutAdvanceAmount={standingAmounts.rolloutAdvanceAmount}
          />
        ) : null}

        {invoice ? <InvoiceSummaryCard kind="AGENT_INVOICE" invoice={invoice} /> : null}
      </div>
    </SurfacePage>
  );
}
