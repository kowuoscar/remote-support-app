import { notFound } from "next/navigation";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { Breadcrumb } from "@/components/app-shell/top-bar";
import { Card } from "@/components/ui/card";
import { AgentStandingAmountsView } from "@/components/manager/agent-standing-amounts-view";
import { ManagerAgentInvoiceView } from "@/components/manager/manager-agent-invoice-view";
import { backendFetch, backendFetchList } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import { countryLabel, type AgentInvoiceDetail, type AgentListItem, type AgentStandingAmounts } from "@/lib/api/types";

export const metadata = { title: "Agent" };

/**
 * An Agent's detail view, added for the Manager to review the Agent, set their standing
 * salary/Rollout Advance (spec.md user stories 5-6; agent-standing-amounts-and-invoice-generation
 * ticket), and review/override/approve/mark-paid their current-month Agent Invoice (spec.md user
 * stories 9-12; agent-invoice-submission-and-approval ticket). Mirrors how
 * `/manager/clients/[clientId]` and `/manager/contracts/[contractId]` were added in prior
 * tickets: there's no single-Agent GET endpoint, so (like those) this finds the Agent in the full
 * list rather than adding one just for this page. The Agent Invoice fetch reuses
 * {@code GET /api/agents/{agentId}/invoice}'s get-or-create semantics (a Manager viewing this
 * page is itself "first access" for a month with no invoice yet — same as the Agent's own
 * `/agent/my-invoice`), so there's always a current-month invoice to review once any Fee/standing
 * amount exists.
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
          <div className="min-w-0">
            <p className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Sign-in email</p>
            {agent.loginUsername ? (
              <p className="mt-1 text-sm break-all text-ink">{agent.loginUsername}</p>
            ) : (
              <p className="mt-1 text-sm text-ink-mute">No login</p>
            )}
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

        <ManagerAgentInvoiceView agentId={agent.id} invoice={invoice} />
      </div>
    </SurfacePage>
  );
}
