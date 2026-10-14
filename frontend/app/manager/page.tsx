import { SurfacePage } from "@/components/app-shell/surface-page";
import { ManagerDashboardStats } from "@/components/manager/dashboard-stats";
import { PendingApprovalsCard } from "@/components/manager/pending-approvals-card";
import { backendFetchJsonOrNull } from "@/lib/api/backend";
import type {
  AgentListItem,
  ClientListItem,
  ContractListItem,
  PendingRequestItem,
  ReviewQueueItem,
  TenantTotals,
} from "@/lib/api/types";

export const metadata = { title: "Dashboard" };

/** The slice of `GET /api/me` the chip reads; `name` arrives with the Manager name (omitted while unset). */
interface Me {
  username: string;
  name?: string;
}

/** `<name> · Manager`, else `<username> · Manager`, else plain "Manager" when the read failed. */
function viewerLabelFor(me: Me | null): string {
  const who = me?.name || me?.username;
  return who ? `${who} · Manager` : "Manager";
}

export default async function ManagerDashboardPage() {
  // Each region loads alone and fails alone: a read that can't be made is `null` (logged), and only
  // its own card goes unavailable. No figure here is scoped by /api/me, so a failed identity read
  // only costs the chip its name.
  const [me, reviewQueue, pendingRequests, totals, clients, agents, contracts] = await Promise.all([
    backendFetchJsonOrNull<Me>("/api/me", "Dashboard: GET /api/me"),
    backendFetchJsonOrNull<ReviewQueueItem[]>("/api/review-queue", "Dashboard: Review Queue"),
    backendFetchJsonOrNull<PendingRequestItem[]>("/api/pending-requests", "Dashboard: Pending Requests"),
    backendFetchJsonOrNull<TenantTotals>("/api/tenant-totals", "Dashboard: GET /api/tenant-totals"),
    backendFetchJsonOrNull<ClientListItem[]>("/api/clients", "Dashboard: GET /api/clients"),
    backendFetchJsonOrNull<AgentListItem[]>("/api/agents", "Dashboard: GET /api/agents"),
    backendFetchJsonOrNull<ContractListItem[]>("/api/contracts", "Dashboard: GET /api/contracts"),
  ]);

  return (
    <SurfacePage title="Dashboard" subtitle="Tenant-wide overview" viewerLabel={viewerLabelFor(me)} viewerCanChangeName>
      <ManagerDashboardStats
        pendingApprovalsCount={reviewQueue?.length ?? null}
        pendingRequestsCount={pendingRequests?.length ?? null}
        billedThisMonth={totals?.billed ?? null}
        payoutThisMonth={totals?.payout ?? null}
        billingMonth={totals?.billingMonth ?? null}
        clientCount={clients?.length ?? null}
        agentCount={agents?.length ?? null}
        contractCount={contracts?.length ?? null}
      />

      <PendingApprovalsCard items={reviewQueue} now={new Date().toISOString()} />
    </SurfacePage>
  );
}
