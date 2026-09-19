import { unstable_rethrow } from "next/navigation";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { ManagerDashboardStats } from "@/components/manager/dashboard-stats";
import { PendingApprovalsCard } from "@/components/manager/pending-approvals-card";
import { backendFetch } from "@/lib/api/backend";
import type { PendingRequestItem, ReviewQueueItem } from "@/lib/api/types";
import { tenantStats } from "@/lib/demo/manager";

export const metadata = { title: "Dashboard" };

/**
 * Reads the Review Queue for the Pending approvals card, or `null` when it can't be loaded. A
 * failure is logged and never thrown: only the card goes unavailable, the rest of the Dashboard
 * renders (manager-invoice-review-queue spec, Constraints) — which is also what lets the
 * backend-free visual suite render this page.
 */
async function loadReviewQueue(): Promise<ReviewQueueItem[] | null> {
  try {
    const response = await backendFetch("/api/review-queue");
    if (!response.ok) {
      console.error(`Dashboard: Review Queue load failed with status ${response.status}`);
      return null;
    }
    return (await response.json()) as ReviewQueueItem[];
  } catch (error) {
    // Next.js signals a request-time render by throwing from cookies(); that must reach Next.js.
    unstable_rethrow(error);
    console.error("Dashboard: Review Queue load failed with no response", error);
    return null;
  }
}

/**
 * Reads the Pending Requests list for the dashboard's own count stat (manager-approves-requests
 * ticket AC: "The Manager's dashboard shows the pending count") — same failure shape as
 * `loadReviewQueue` above: a failed load only takes the one stat unavailable, never the page.
 */
async function loadPendingRequests(): Promise<PendingRequestItem[] | null> {
  try {
    const response = await backendFetch("/api/pending-requests");
    if (!response.ok) {
      console.error(`Dashboard: Pending Requests load failed with status ${response.status}`);
      return null;
    }
    return (await response.json()) as PendingRequestItem[];
  } catch (error) {
    unstable_rethrow(error);
    console.error("Dashboard: Pending Requests load failed with no response", error);
    return null;
  }
}

export default async function ManagerDashboardPage() {
  const [reviewQueue, pendingRequests] = await Promise.all([loadReviewQueue(), loadPendingRequests()]);

  return (
    <SurfacePage
      title="Dashboard"
      subtitle="Tenant-wide overview"
      viewerLabel="Priya Ashford · Manager"
    >
      <ManagerDashboardStats
        pendingApprovalsCount={reviewQueue?.length ?? null}
        pendingRequestsCount={pendingRequests?.length ?? null}
        billedThisMonth={tenantStats.billedThisMonthUSD}
        payoutThisMonth={tenantStats.payoutThisMonthUSD}
        clientCount={tenantStats.clientCount}
        agentCount={tenantStats.agentCount}
        contractCount={tenantStats.contractCount}
      />

      <PendingApprovalsCard items={reviewQueue} now={new Date().toISOString()} />
    </SurfacePage>
  );
}
