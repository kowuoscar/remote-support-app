"use client";

import { SurfacePage } from "@/components/app-shell/surface-page";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle } from "@/components/icons";

/**
 * Shown when the Client dashboard throws — today, when the Tester's own Client (`GET /api/me/client`)
 * or login (`GET /api/me`) cannot be read. The `(dashboard)` route group scopes it to `/client`
 * alone; the other `/client/*` pages keep the framework default. The header names no Client.
 */
export default function ClientDashboardError() {
  return (
    <SurfacePage title="Dashboard" subtitle="Your Client dashboard" viewerLabel="Tester">
      <Card className="p-0" data-testid="dashboard-unavailable">
        <div className="p-5">
          <EmptyState
            icon={<IconAlertTriangle className="h-5 w-5" />}
            title="Couldn't load your dashboard — reload the page to try again"
          />
        </div>
      </Card>
    </SurfacePage>
  );
}
