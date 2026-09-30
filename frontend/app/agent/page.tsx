import Link from "next/link";
import { unstable_rethrow } from "next/navigation";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentDashboardStats } from "@/components/agent/dashboard-stats";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle, IconArrowRight, IconRequests } from "@/components/icons";
import { backendFetch } from "@/lib/api/backend";
import { countryLabel, type AgentInvoiceStatusValue, type AgentOwnRecord } from "@/lib/api/types";
import { formatRelativeAge } from "@/lib/format";
import { requestStatusTone } from "@/lib/status";
import {
  agentRequests,
  currentMonthLabel,
  myAgentInvoices,
  runningLocalSupportFees,
} from "@/lib/demo/agent";

export const metadata = { title: "Dashboard" };

type Identity =
  | { kind: "agent"; agent: AgentOwnRecord }
  | { kind: "not-linked" }
  | { kind: "failed" };

/**
 * Reads the caller's own Agent. A 404 means the login is not linked to an Agent; any other
 * failure is logged with the endpoint and status and makes the whole page unavailable, because
 * every figure on it is scoped by this read.
 */
async function loadIdentity(): Promise<Identity> {
  try {
    const response = await backendFetch("/api/me/agent");
    if (response.status === 404) return { kind: "not-linked" };
    if (!response.ok) {
      console.error(`Agent dashboard: GET /api/me/agent failed with status ${response.status}`);
      return { kind: "failed" };
    }
    return { kind: "agent", agent: (await response.json()) as AgentOwnRecord };
  } catch (error) {
    // Next.js signals a request-time render by throwing from cookies(); that must reach Next.js.
    unstable_rethrow(error);
    console.error("Agent dashboard: GET /api/me/agent failed with no response", error);
    return { kind: "failed" };
  }
}

function PageUnavailable({ title, description }: { title: string; description: string }) {
  return (
    <SurfacePage title="Dashboard" subtitle="Your Agent console" viewerLabel="Agent">
      <Card className="p-0" data-testid="dashboard-unavailable">
        <div className="p-5">
          <EmptyState
            icon={<IconAlertTriangle className="h-5 w-5" />}
            title={title}
            description={description}
          />
        </div>
      </Card>
    </SurfacePage>
  );
}

export default async function AgentDashboardPage() {
  const identity = await loadIdentity();
  if (identity.kind === "not-linked") {
    return (
      <PageUnavailable
        title="Your login isn't linked to an Agent record yet"
        description="Ask your Manager to link your login to your Agent record before you can see your dashboard."
      />
    );
  }
  if (identity.kind === "failed") {
    return (
      <PageUnavailable
        title="Couldn't load your dashboard — reload the page to try again"
        description="Your name and standing amounts come from your Agent record, which could not be read."
      />
    );
  }
  const { agent } = identity;

  const openRequests = agentRequests.filter(
    (r) => r.status === "Submitted" || r.status === "In Progress",
  );
  const latestInvoice = myAgentInvoices[0];
  const recentRequests = [...agentRequests]
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
    .slice(0, 5);

  return (
    <SurfacePage
      title="Dashboard"
      subtitle={`${agent.name} · ${countryLabel(agent.country)}`}
      viewerLabel={`${agent.name} · Agent`}
    >
      <AgentDashboardStats
        currentMonthLabel={currentMonthLabel}
        runningLocalSupportFees={runningLocalSupportFees}
        currency={agent.currency}
        openRequestsCount={openRequests.length}
        latestInvoiceMonth={latestInvoice.month}
        latestInvoiceStatus={latestInvoice.status.toUpperCase() as AgentInvoiceStatusValue}
        salary={agent.salaryAmount}
        rolloutAdvance={agent.rolloutAdvanceAmount}
      />

      <Card className="p-0">
        <div className="flex items-center justify-between border-b border-hairline px-5 py-4">
          <div>
            <h2 className="text-sm font-semibold text-ink">Recent Requests</h2>
            <p className="text-[13px] text-ink-mute">Across both your Contracts</p>
          </div>
          <Link
            href="/agent/requests"
            className="inline-flex items-center gap-1 text-[13px] font-medium text-primary hover:text-primary-hover"
          >
            Open queue
            <IconArrowRight className="h-3.5 w-3.5" />
          </Link>
        </div>
        <ul className="divide-y divide-hairline">
          {recentRequests.map((request) => (
            <li key={request.id} className="flex items-center justify-between gap-4 px-5 py-3.5">
              <div className="flex min-w-0 items-center gap-3">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-canvas-soft text-ink-mute">
                  <IconRequests className="h-4 w-4" />
                </span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-ink">
                    {request.type} · {request.contractLabel}
                  </p>
                  <p className="truncate text-[12px] text-ink-mute">
                    Raised by {request.raisedBy} · updated {formatRelativeAge(request.updatedAt)}
                  </p>
                </div>
              </div>
              <Badge tone={requestStatusTone[request.status]}>{request.status}</Badge>
            </li>
          ))}
        </ul>
      </Card>
    </SurfacePage>
  );
}
