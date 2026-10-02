import Link from "next/link";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { ClientDashboardStats } from "@/components/client/dashboard-stats";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Money } from "@/components/ui/money";
import { IconArrowRight } from "@/components/icons";
import { clientInvoiceStatusLabel, clientInvoiceStatusTone } from "@/lib/status";
import { monthLabelSortKey } from "@/lib/format";
import { clientContracts, clientInvoices } from "@/lib/demo/client";
import { backendFetch, backendFetchJsonOrNull } from "@/lib/api/backend";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle } from "@/components/icons";
import type {
  ClientOwnRecord,
  ContractListItem,
  RequestListItem,
  SimCardListItem,
  SmartphoneListItem,
} from "@/lib/api/types";

export const metadata = { title: "Dashboard" };

/**
 * Reads the caller's own Client. A 404 means the login is not linked to a Client, which the page
 * renders as a message; any other failure is logged with its endpoint and status, then thrown to
 * this segment's `error.tsx`, because every figure on the page is scoped by this read.
 */
async function loadClient(): Promise<ClientOwnRecord | null> {
  const response = await backendFetch("/api/me/client");
  if (response.status === 404) return null;
  if (!response.ok) {
    console.error(`Client dashboard: GET /api/me/client failed with status ${response.status}`);
    throw new Error(`Client dashboard: GET /api/me/client failed with status ${response.status}`);
  }
  return (await response.json()) as ClientOwnRecord;
}

/** Reads the caller's login name for the viewer chip; a failure is logged, then thrown. */
async function loadUsername(): Promise<string> {
  const response = await backendFetch("/api/me");
  if (!response.ok) {
    console.error(`Client dashboard: GET /api/me failed with status ${response.status}`);
    throw new Error(`Client dashboard: GET /api/me failed with status ${response.status}`);
  }
  return ((await response.json()) as { username: string }).username;
}

/**
 * How many of one Contract's rows at `path` satisfy `isCounted`, or `null` when the read failed.
 * The failure is logged with the endpoint and status by `backendFetchJsonOrNull`.
 */
async function countContractRows<T>(
  contractId: string,
  resource: "smartphones" | "sim-cards" | "requests",
  isCounted: (row: T) => boolean,
): Promise<number | null> {
  const path = `/api/contracts/${contractId}/${resource}`;
  const rows = await backendFetchJsonOrNull<T[]>(path, `GET ${path}`);
  return rows === null ? null : rows.filter(isCounted).length;
}

/**
 * Sums per-Contract counts, or returns `null` when the Contract list or any one count failed.
 * All-or-nothing on purpose: a figure that silently dropped a failed Contract would look like real
 * data, which is why this page does not use `backendFetchList`.
 */
async function sumAcrossContracts(
  contracts: ContractListItem[] | null,
  countFor: (contract: ContractListItem) => Promise<(number | null)[]>,
): Promise<number | null> {
  if (contracts === null) return null;
  const counts = (await Promise.all(contracts.map(countFor))).flat();
  if (counts.includes(null)) return null;
  return counts.reduce<number>((sum, count) => sum + (count ?? 0), 0);
}

const ACTIVE_SMARTPHONE_STATUSES: readonly SmartphoneListItem["status"][] = ["ACTIVE", "IN_REPAIR"];
const OPEN_REQUEST_STATUSES: readonly RequestListItem["status"][] = [
  "PENDING_APPROVAL",
  "SUBMITTED",
  "IN_PROGRESS",
];

export default async function ClientDashboardPage() {
  const [client, username] = await Promise.all([loadClient(), loadUsername()]);
  if (client === null) {
    return (
      <SurfacePage title="Dashboard" subtitle="Your Client dashboard" viewerLabel={`${username} · Tester`}>
        <Card className="p-0" data-testid="dashboard-unavailable">
          <div className="p-5">
            <EmptyState
              icon={<IconAlertTriangle className="h-5 w-5" />}
              title="Your login isn't linked to a Client yet"
              description="Ask your Manager to link your login to your Client before you can see your dashboard."
            />
          </div>
        </Card>
      </SurfacePage>
    );
  }

  const contracts = await backendFetchJsonOrNull<ContractListItem[]>("/api/contracts", "GET /api/contracts");
  const [activeFleetCount, openRequestsCount] = await Promise.all([
    sumAcrossContracts(contracts, (contract) =>
      Promise.all([
        countContractRows<SmartphoneListItem>(contract.id, "smartphones", (p) =>
          ACTIVE_SMARTPHONE_STATUSES.includes(p.status),
        ),
        countContractRows<SimCardListItem>(contract.id, "sim-cards", (s) => s.status === "ACTIVE"),
      ]),
    ),
    sumAcrossContracts(contracts, async (contract) => [
      await countContractRows<RequestListItem>(contract.id, "requests", (r) =>
        OPEN_REQUEST_STATUSES.includes(r.status),
      ),
    ]),
  ]);

  // A Client Invoice is visible to Testers only once the Agent has sent it —
  // drafts never appear here, matching the Invoices list.
  const latestByContract = clientContracts.map((contract) => {
    const invoicesForContract = clientInvoices
      .filter((inv) => inv.contractId === contract.id && inv.status !== "draft")
      .sort((a, b) => monthLabelSortKey(b.month) - monthLabelSortKey(a.month));
    return { contract, latest: invoicesForContract[0] };
  });

  return (
    <SurfacePage
      title="Dashboard"
      subtitle={client.name}
      wrapSubtitle
      viewerLabel={`${username} · Tester`}
      demoData
    >
      <ClientDashboardStats activeFleetCount={activeFleetCount} openRequestsCount={openRequestsCount} />

      <Card className="p-0">
        <div className="flex items-center justify-between border-b border-hairline px-5 py-4">
          <div>
            <h2 className="text-sm font-semibold text-ink">Latest Client Invoice</h2>
            <p className="text-[13px] text-ink-mute">Most recent month per Contract</p>
          </div>
          <Link
            href="/client/invoices"
            className="inline-flex items-center gap-1 text-[13px] font-medium text-primary hover:text-primary-hover"
          >
            View all
            <IconArrowRight className="h-3.5 w-3.5" />
          </Link>
        </div>
        <ul className="divide-y divide-hairline">
          {latestByContract.map(({ contract, latest }) => (
            <li key={contract.id} className="flex items-center justify-between gap-4 px-5 py-3.5">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-ink">{contract.label}</p>
                {latest ? (
                  <p className="truncate text-[12px] text-ink-mute">{latest.month}</p>
                ) : null}
              </div>
              {latest ? (
                <div className="flex shrink-0 items-center gap-3">
                  <Badge tone={clientInvoiceStatusTone[latest.status]}>
                    {clientInvoiceStatusLabel[latest.status]}
                  </Badge>
                  <Money amount={latest.totalAmount} currency={latest.currency} className="text-sm font-medium" />
                </div>
              ) : (
                <span className="text-[13px] text-ink-mute">No invoices yet</span>
              )}
            </li>
          ))}
        </ul>
      </Card>
    </SurfacePage>
  );
}
