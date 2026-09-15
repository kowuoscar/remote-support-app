import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle, IconMyInvoice } from "@/components/icons";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { AgentInvoiceView } from "@/components/agent/agent-invoice-view";
import { backendFetch } from "@/lib/api/backend";
import type { AgentInvoiceDetail } from "@/lib/api/types";

export const metadata = { title: "My Invoice" };

/**
 * Real backend wiring for the Agent's own monthly Agent Invoice
 * (agent-standing-amounts-and-invoice-generation ticket, user stories 25-26). {@code /api/me}
 * resolves the caller's own Agent id (unlinked logins get {@code agentId: null} and see the
 * "not linked" empty state below); fetching the invoice is itself what creates this month's
 * draft on first access (see AgentInvoiceController's Javadoc) — no separate "start the invoice"
 * step, mirroring how /agent/client-invoices already works for Client Invoices.
 */
export default async function AgentMyInvoicePage() {
  const meResponse = await backendFetch("/api/me");
  const me = meResponse.ok ? ((await meResponse.json()) as { username?: string; agentId?: string }) : {};

  const invoiceResponse = me.agentId ? await backendFetch(`/api/agents/${me.agentId}/invoice`) : null;
  const invoice: AgentInvoiceDetail | null =
    invoiceResponse && invoiceResponse.ok ? ((await invoiceResponse.json()) as AgentInvoiceDetail) : null;

  return (
    <SurfacePage
      title="My Invoice"
      subtitle="Local Support Fees, salary and Rollout Advance"
      viewerLabel={`${me.username ?? "Agent"} · Agent`}
    >
      {!me.agentId ? (
        <Card className="p-0">
          <div className="p-5">
            <EmptyState
              icon={<IconAlertTriangle className="h-5 w-5" />}
              title="Your login isn't linked to an Agent record yet"
              description="Ask your Manager to link your login to your Agent record before you can build a monthly invoice."
            />
          </div>
        </Card>
      ) : !invoice ? (
        <Card className="p-0">
          <div className="p-5">
            <EmptyState
              icon={<IconMyInvoice className="h-5 w-5" />}
              title="Couldn't load this month's invoice"
              description="Reload the page to try again."
            />
          </div>
        </Card>
      ) : (
        <AgentInvoiceView invoice={invoice} />
      )}
    </SurfacePage>
  );
}
