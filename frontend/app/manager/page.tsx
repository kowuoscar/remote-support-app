import Link from "next/link";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { ManagerDashboardStats } from "@/components/manager/dashboard-stats";
import { Card } from "@/components/ui/card";
import { Money } from "@/components/ui/money";
import { IconArrowRight, IconInvoices } from "@/components/icons";
import { formatRelativeAge } from "@/lib/format";
import { pendingApprovals, tenantStats } from "@/lib/demo/manager";

export const metadata = { title: "Dashboard" };

export default function ManagerDashboardPage() {
  const preview = pendingApprovals.slice(0, 4);

  return (
    <SurfacePage
      title="Dashboard"
      subtitle="Tenant-wide overview"
      viewerLabel="Priya Ashford · Manager"
    >
      <ManagerDashboardStats
        pendingApprovalsCount={pendingApprovals.length}
        billedThisMonth={tenantStats.billedThisMonthUSD}
        payoutThisMonth={tenantStats.payoutThisMonthUSD}
        clientCount={tenantStats.clientCount}
        agentCount={tenantStats.agentCount}
        contractCount={tenantStats.contractCount}
      />

      <Card className="p-0">
        <div className="flex items-center justify-between border-b border-hairline px-5 py-4">
          <div>
            <h2 className="text-sm font-semibold text-ink">Pending approvals</h2>
            <p className="text-[13px] text-ink-mute">Oldest submissions first</p>
          </div>
          <Link
            href="/manager/invoices"
            className="inline-flex items-center gap-1 text-[13px] font-medium text-primary hover:text-primary-hover"
          >
            Review all
            <IconArrowRight className="h-3.5 w-3.5" />
          </Link>
        </div>
        <ul className="divide-y divide-hairline">
          {preview.map((item) => (
            <li key={item.id} className="flex items-center justify-between gap-4 px-5 py-3.5">
              <div className="flex min-w-0 items-center gap-3">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-canvas-soft text-ink-mute">
                  <IconInvoices className="h-4 w-4" />
                </span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-ink">{item.subject}</p>
                  <p className="truncate text-[12px] text-ink-mute">
                    {item.kind} · {item.month} · submitted {formatRelativeAge(item.submittedAt)}
                  </p>
                </div>
              </div>
              <Money amount={item.amount} currency={item.currency} className="shrink-0 text-sm font-medium" />
            </li>
          ))}
        </ul>
      </Card>
    </SurfacePage>
  );
}
