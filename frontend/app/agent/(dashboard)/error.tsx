"use client";

import { SurfacePage } from "@/components/app-shell/surface-page";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle } from "@/components/icons";

/**
 * Shown when the Agent dashboard throws — today, when the Agent's own record (`GET /api/me/agent`)
 * cannot be read. The `(dashboard)` route group scopes it to `/agent` alone; the other `/agent/*`
 * pages keep the framework default.
 */
export default function AgentDashboardError() {
  return (
    <SurfacePage title="Dashboard" subtitle="Your Agent console" viewerLabel="Agent">
      <Card className="p-0" data-testid="dashboard-unavailable">
        <div className="p-5">
          <EmptyState
            icon={<IconAlertTriangle className="h-5 w-5" />}
            title="Couldn't load your dashboard — reload the page to try again"
            description="Your name and standing amounts come from your Agent record, which could not be read."
          />
        </div>
      </Card>
    </SurfacePage>
  );
}
