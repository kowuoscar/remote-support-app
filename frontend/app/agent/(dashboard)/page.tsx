import Link from "next/link";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentDashboardStats } from "@/components/agent/dashboard-stats";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle, IconArrowRight, IconRequests } from "@/components/icons";
import { backendFetch, backendFetchJsonOrNull } from "@/lib/api/backend";
import {
  REQUEST_STATUS_LABEL,
  REQUEST_TYPE_LABEL,
  countryLabel,
  type AgentInvoiceDetail,
  type AgentOwnRecord,
  type ContractListItem,
  type RequestListItem,
} from "@/lib/api/types";
import { formatBillingMonth, formatRelativeAge } from "@/lib/format";
import { requestStatusToneByValue } from "@/lib/status";

export const metadata = { title: "Dashboard" };

/**
 * Reads the caller's own Agent. A 404 means the login is not linked to an Agent, which the page
 * renders as a message; any other failure is thrown to this segment's `error.tsx`, because every
 * figure on the page is scoped by this read.
 */
async function loadAgent(): Promise<AgentOwnRecord | null> {
  const response = await backendFetch("/api/me/agent");
  if (response.status === 404) return null;
  if (!response.ok) {
    throw new Error(`Agent dashboard: GET /api/me/agent failed with status ${response.status}`);
  }
  return (await response.json()) as AgentOwnRecord;
}

type AgentRequest = RequestListItem & { contractLabel: string };

/**
 * Every Request of every one of the Agent's own Contracts, each carrying its Contract's label, or
 * `null` when the Contract list or any one Contract's Requests could not be read. All-or-nothing
 * on purpose: a count or a list that silently dropped a failed Contract would look like real data.
 */
async function loadAgentRequests(): Promise<AgentRequest[] | null> {
  const contracts = await backendFetchJsonOrNull<ContractListItem[]>("/api/contracts", "GET /api/contracts");
  if (contracts === null) return null;
  const perContract = await Promise.all(
    contracts.map(async (contract) => {
      const requests = await backendFetchJsonOrNull<RequestListItem[]>(
        `/api/contracts/${contract.id}/requests`,
        `GET /api/contracts/${contract.id}/requests`,
      );
      const contractLabel = `${contract.clientName} — ${countryLabel(contract.country)}`;
      return requests?.map((request) => ({ ...request, contractLabel })) ?? null;
    }),
  );
  if (perContract.some((requests) => requests === null)) return null;
  return perContract.flatMap((requests) => requests ?? []);
}

const OPEN_STATUSES: readonly RequestListItem["status"][] = ["SUBMITTED", "IN_PROGRESS"];

function RecentRequestsBody({ requests, now }: { requests: AgentRequest[] | null; now: Date }) {
  if (requests === null) {
    return (
      <div className="p-5">
        <EmptyState
          icon={<IconAlertTriangle className="h-5 w-5" />}
          title="Couldn't load your Requests"
          description="Reload the page to try again."
        />
      </div>
    );
  }
  if (requests.length === 0) {
    return (
      <div className="p-5">
        <EmptyState
          icon={<IconRequests className="h-5 w-5" />}
          title="No Requests yet"
          description="Requests from your Contracts' Testers will show up here."
        />
      </div>
    );
  }
  const recent = [...requests]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 5);
  return (
    <ul className="divide-y divide-hairline" data-testid="recent-requests">
      {recent.map((request) => (
        <li key={request.id} className="flex items-center justify-between gap-4 px-5 py-3.5">
          <div className="flex min-w-0 items-center gap-3">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-canvas-soft text-ink-mute">
              <IconRequests className="h-4 w-4" />
            </span>
            <div className="min-w-0">
              <p className="line-clamp-2 text-sm font-medium text-ink">
                {REQUEST_TYPE_LABEL[request.type]} · {request.contractLabel}
              </p>
              {/* 12px: the app-wide metadata size, which no tailwind.config token provides. */}
              <p className="line-clamp-2 text-[12px] text-ink-mute">
                Raised by {request.raisedByUsername} · raised{" "}
                <time dateTime={request.createdAt}>{formatRelativeAge(request.createdAt, now)}</time>
              </p>
            </div>
          </div>
          <Badge tone={requestStatusToneByValue[request.status]}>
            {REQUEST_STATUS_LABEL[request.status]}
          </Badge>
        </li>
      ))}
    </ul>
  );
}

export default async function AgentDashboardPage() {
  const agent = await loadAgent();
  if (agent === null) {
    return (
      <SurfacePage title="Dashboard" subtitle="Your Agent console" viewerLabel="Agent">
        <Card className="p-0" data-testid="dashboard-unavailable">
          <div className="p-5">
            <EmptyState
              icon={<IconAlertTriangle className="h-5 w-5" />}
              title="Your login isn't linked to an Agent record yet"
              description="Ask your Manager to link your login to your Agent record before you can see your dashboard."
            />
          </div>
        </Card>
      </SurfacePage>
    );
  }
  // The same get-or-create read My Invoice uses: its first visit of the month creates the Draft.
  const [invoice, requests] = await Promise.all([
    backendFetchJsonOrNull<AgentInvoiceDetail>(
      `/api/agents/${agent.agentId}/invoice`,
      `GET /api/agents/${agent.agentId}/invoice`,
    ),
    loadAgentRequests(),
  ]);
  const billingMonth = invoice ? formatBillingMonth(invoice.billingMonth) : null;
  // One clock for the whole render, so every age on the page is measured from the same instant.
  const now = new Date();
  const openRequestsCount =
    requests === null ? null : requests.filter((r) => OPEN_STATUSES.includes(r.status)).length;

  return (
    <SurfacePage
      title="Dashboard"
      subtitle={`${agent.name} · ${countryLabel(agent.country)}`}
      wrapSubtitle
      viewerLabel={`${agent.name} · Agent`}
    >
      <AgentDashboardStats
        currentMonthLabel={billingMonth}
        runningLocalSupportFees={invoice?.localSupportFees ?? null}
        currency={agent.currency}
        openRequestsCount={openRequestsCount}
        latestInvoiceMonth={billingMonth}
        latestInvoiceStatus={invoice?.status ?? null}
        salary={agent.salaryAmount}
        rolloutAdvance={agent.rolloutAdvanceAmount}
      />

      <Card className="p-0">
        <div className="flex items-center justify-between border-b border-hairline px-5 py-4">
          <div>
            <h2 className="text-sm font-semibold text-ink">Recent Requests</h2>
            {/* 13px: the app-wide caption size, which no tailwind.config token provides. */}
            <p className="text-[13px] text-ink-mute">Across all your Contracts</p>
          </div>
          <Link
            href="/agent/requests"
            className="inline-flex items-center gap-1 text-[13px] font-medium text-primary hover:text-primary-hover"
          >
            Open queue
            <IconArrowRight className="h-3.5 w-3.5" />
          </Link>
        </div>
        <RecentRequestsBody requests={requests} now={now} />
      </Card>
    </SurfacePage>
  );
}
