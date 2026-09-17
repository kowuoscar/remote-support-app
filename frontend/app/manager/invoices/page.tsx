import { SurfacePage } from "@/components/app-shell/surface-page";
import { ReviewQueueView } from "@/components/manager/review-queue-view";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle } from "@/components/icons";
import { backendFetch } from "@/lib/api/backend";
import { requireManager } from "@/lib/api/guard";
import type { ReviewQueueItem } from "@/lib/api/types";

export const metadata = { title: "Invoices" };

/**
 * The Manager's Review Queue (CONTEXT.md; manager-invoice-review-queue spec, Invoices page), read
 * from the backend on every request.
 */
export default async function ManagerInvoicesPage() {
  await requireManager();
  const response = await backendFetch("/api/review-queue");
  const items = response.ok ? ((await response.json()) as ReviewQueueItem[]) : null;

  return (
    <SurfacePage title="Invoices" subtitle="Waiting on you, longest waiting first" viewerLabel="Manager">
      {items ? (
        <ReviewQueueView items={items} now={new Date().toISOString()} />
      ) : (
        <EmptyState
          icon={<IconAlertTriangle className="h-5 w-5" />}
          title="Couldn't load the Review Queue"
          description="Refresh the page to try again."
        />
      )}
    </SurfacePage>
  );
}
