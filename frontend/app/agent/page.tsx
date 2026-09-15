import Link from "next/link";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentDashboardStats } from "@/components/agent/dashboard-stats";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { IconArrowRight, IconRequests } from "@/components/icons";
import { formatRelativeAge } from "@/lib/format";
import { requestStatusTone } from "@/lib/status";
import {
  agentRequests,
  currentAgent,
  currentMonthLabel,
  myAgentInvoices,
  runningLocalSupportFees,
} from "@/lib/demo/agent";

export const metadata = { title: "Dashboard" };

export default function AgentDashboardPage() {
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
      subtitle={`${currentAgent.name} · ${currentAgent.country}`}
      viewerLabel={`${currentAgent.name} · Agent`}
    >
      <AgentDashboardStats
        currentMonthLabel={currentMonthLabel}
        runningLocalSupportFees={runningLocalSupportFees}
        currency={currentAgent.currency}
        openRequestsCount={openRequests.length}
        latestInvoiceMonth={latestInvoice.month}
        latestInvoiceStatus={latestInvoice.status}
        salary={currentAgent.salary}
        rolloutAdvance={currentAgent.rolloutAdvance}
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
