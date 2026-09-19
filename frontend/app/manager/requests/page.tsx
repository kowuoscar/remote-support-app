import { SurfacePage } from "@/components/app-shell/surface-page";
import { PendingRequestsView } from "@/components/manager/pending-requests-view";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle } from "@/components/icons";
import { backendFetch } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import type { PendingRequestItem } from "@/lib/api/types";

export const metadata = { title: "Requests" };

/**
 * The Manager's Pending Requests page (CONTEXT.md "Pending Requests"; request-types-and-flow
 * spec, Manager approval; manager-approves-requests ticket), read from the backend on every
 * request — mirrors {@code app/manager/invoices/page.tsx}'s Review Queue shape exactly.
 */
export default async function ManagerRequestsPage() {
  await requireManager();
  const response = await backendFetch("/api/pending-requests");
  const items = response.ok ? ((await response.json()) as PendingRequestItem[]) : null;

  return (
    <SurfacePage title="Requests" subtitle="Pending Approval, longest waiting first" viewerLabel="Manager">
      {items ? (
        <PendingRequestsView items={items} now={new Date().toISOString()} />
      ) : (
        <EmptyState
          icon={<IconAlertTriangle className="h-5 w-5" />}
          title="Couldn't load Pending Requests"
          description="Refresh the page to try again."
        />
      )}
    </SurfacePage>
  );
}
