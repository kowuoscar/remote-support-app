import type { ReactNode } from "react";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Money } from "@/components/ui/money";
import { IconAlertTriangle, IconArrowRight, IconInbox, IconInvoices } from "@/components/icons";
import { formatBillingMonth, formatWaitingTime } from "@/lib/format";
import type { ReviewQueueItem } from "@/lib/api/types";

/** The Dashboard shows at most this many Review Queue items (manager-invoice-review-queue spec, Constraints). */
const PREVIEW_SIZE = 4;

interface QueueEntry {
  href: string;
  kindLabel: string;
  subject: string;
}

/**
 * How one Review Queue item reads on the card, decided by its `kind`: a Client Invoice is about a
 * Contract (Client — Agent), an Agent Invoice about its Agent.
 */
function describeEntry(item: ReviewQueueItem): QueueEntry {
  if (item.kind === "AGENT_INVOICE") {
    return {
      href: `/manager/invoices/agent/${item.id}`,
      kindLabel: "Agent Invoice",
      subject: item.agentName,
    };
  }
  return {
    href: `/manager/invoices/client/${item.id}`,
    kindLabel: "Client Invoice",
    subject: `${item.clientName} — ${item.agentName}`,
  };
}

function waitingLabel(waitingSince: string, now: string): string {
  const waited = formatWaitingTime(waitingSince, now);
  return waited === "Today" ? "waiting since today" : `waiting ${waited}`;
}

function CardMessage({ icon, title, description }: { icon: ReactNode; title: string; description: string }) {
  return (
    <div className="flex items-center gap-3 px-5 py-6">
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-canvas-soft text-ink-mute transition-colors group-hover:bg-canvas">
        {icon}
      </span>
      <div className="min-w-0">
        <p className="text-sm font-medium text-ink">{title}</p>
        <p className="text-[13px] text-ink-mute">{description}</p>
      </div>
    </div>
  );
}

/**
 * The Manager Dashboard's entry point into the Review Queue (CONTEXT.md): the longest-waiting
 * invoices, each opening its detail page. `items` is the whole queue in the backend's order, or
 * `null` when it couldn't be loaded, in which case only this card says so. `now` comes from the
 * server so waiting times render the same on server and client.
 */
export function PendingApprovalsCard({ items, now }: { items: ReviewQueueItem[] | null; now: string }) {
  return (
    <Card className="p-0" data-testid="pending-approvals-card">
      <div className="flex items-center justify-between gap-4 border-b border-hairline px-5 py-4">
        <div>
          <h2 id="pending-approvals-heading" className="text-sm font-semibold text-ink">
            Pending approvals
          </h2>
          <p className="text-[13px] text-ink-mute">Longest waiting first</p>
        </div>
        <Link
          href="/manager/invoices"
          className="inline-flex shrink-0 items-center gap-1 text-[13px] font-medium text-primary hover:text-primary-hover"
        >
          Review all
          <IconArrowRight className="h-3.5 w-3.5" />
        </Link>
      </div>

      {items === null ? (
        <CardMessage
          icon={<IconAlertTriangle className="h-4 w-4" />}
          title="Couldn’t load the Review Queue"
          description="Refresh the page to try again."
        />
      ) : items.length === 0 ? (
        <CardMessage
          icon={<IconInbox className="h-4 w-4" />}
          title="Nothing is waiting on you"
          description="Invoices appear here as soon as an Agent sends them."
        />
      ) : (
        <ul aria-labelledby="pending-approvals-heading" className="divide-y divide-hairline">
          {items.slice(0, PREVIEW_SIZE).map((item) => {
            const entry = describeEntry(item);
            return (
              <li key={`${item.kind}-${item.id}`} className="group">
                <Link
                  href={entry.href}
                  className="flex items-center justify-between gap-4 px-5 py-3.5 transition-colors group-last:rounded-b-xl hover:bg-canvas-soft"
                >
                  <span className="flex min-w-0 items-center gap-3">
                    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-canvas-soft text-ink-mute transition-colors group-hover:bg-canvas">
                      <IconInvoices className="h-4 w-4" />
                    </span>
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium text-ink">{entry.subject}</span>
                      <span className="block text-[12px] text-ink-mute">
                        {entry.kindLabel} · {formatBillingMonth(item.billingMonth)} ·{" "}
                        {waitingLabel(item.waitingSince, now)}
                      </span>
                    </span>
                  </span>
                  <Money amount={item.totalAmount} currency={item.currency} className="shrink-0 text-sm font-medium" />
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
