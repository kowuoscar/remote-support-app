"use client";

import { useMemo, useRef, useState } from "react";
import Link from "next/link";
import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { ContractSideList } from "@/components/agent/contract-side-list";
import { cn } from "@/lib/cn";
import { clientInvoiceBadge, isSentBackBadge } from "@/lib/status";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle, IconArrowRight, IconInvoices } from "@/components/icons";
import { Card } from "@/components/ui/card";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { AgentClientInvoiceCard } from "@/components/agent/agent-client-invoice-card";
import { PAGE_PADDING, PAGE_WIDTH } from "@/components/app-shell/surface-page";
import { Breadcrumb } from "@/components/app-shell/top-bar";
import { formatBillingMonth, formatDate } from "@/lib/format";
import { countryLabel, type ClientInvoiceDetail, type SentBackClientInvoice } from "@/lib/api/types";

/** What the page passes per Contract; the view derives the switcher's options and the side list's rows. */
export type ClientInvoicesContract = {
  id: string;
  clientName: string;
  /** "Kenya", from `countryLabel(country)`. */
  countryLabel: string;
  currency: string;
};


/** Column order: Client name, then Country, locale-aware and case-insensitive. */
function sortContracts(contracts: ClientInvoicesContract[]): ClientInvoicesContract[] {
  const collator = new Intl.Collator(undefined, { sensitivity: "base" });
  return [...contracts].sort(
    (a, b) => collator.compare(a.clientName, b.clientName) || collator.compare(a.countryLabel, b.countryLabel),
  );
}

/**
 * Agent opens a Contract's current-month Client Invoice draft (client-invoice-generation ticket
 * AC: "Agent can open a Contract's Client Invoice for the current month ... The draft view is
 * scannable at a glance: base amount, each Fee line, and attached files are all visible
 * together"). Mirrors the Contract-switcher shape Requests/Fleet already established; the summary
 * row (base/fees/total) is the "at a glance" part, with Fee lines and files as simple lists below
 * rather than buried behind further navigation.
 */
export function AgentClientInvoicesView({
  contracts,
  invoicesByContract,
  sentBack = [],
  initialContractId,
}: {
  contracts: ClientInvoicesContract[];
  invoicesByContract: Record<string, ClientInvoiceDetail | null>;
  /** The Agent's sent-back invoices from any month, oldest first; the section is absent when empty. */
  sentBack?: SentBackClientInvoice[];
  /** The page's `contract` search parameter, unvalidated: used only when it names one of `contracts`. */
  initialContractId?: string;
}) {
  const ordered = useMemo(() => sortContracts(contracts), [contracts]);
  const [contractId, setContractId] = useState(
    () => ordered.find((c) => c.id === initialContractId)?.id ?? ordered[0]?.id ?? "",
  );
  const sentBackContracts = useMemo(() => new Set(sentBack.map((row) => row.contractId)), [sentBack]);
  const invoiceAreaRef = useRef<HTMLDivElement>(null);
  const invoice = invoicesByContract[contractId];

  function select(id: string) {
    setContractId(id);
    // The address follows the selection with the native History API: no entry, no server render,
    // no request. Never router.push/replace, which would re-run the page's reads.
    const url = new URL(window.location.href);
    url.searchParams.set("contract", id);
    window.history.replaceState(window.history.state, "", url);
    // The document scrolls the invoice; return to its top when the new invoice would start off-screen.
    if (invoiceAreaRef.current && invoiceAreaRef.current.getBoundingClientRect().top < 0) {
      window.scrollTo({ top: 0 });
    }
  }

  if (ordered.length === 0) {
    return (
      <div className={PAGE_PADDING}>
        <div className={PAGE_WIDTH}>
          <EmptyState
            icon={<IconInvoices className="h-5 w-5" />}
            title="No contracts yet"
            description="Once a manager creates a contract for you, its monthly Client Invoice shows up here."
          />
        </div>
      </div>
    );
  }

  const switcherOptions: ContractOption[] = ordered.map((c) => ({
    id: c.id,
    label: `${c.clientName} — ${c.countryLabel}`,
    meta: c.countryLabel,
    currency: c.currency,
  }));

  return (
    // Below xl: today's page, padded and capped here because the page is `bleed`. At xl: the side
    // list beside the invoice area.
    <div className="xl:flex xl:items-start">
      <ContractSideList
        contracts={ordered.map((c) => {
          const current = invoicesByContract[c.id];
          const status = current ? clientInvoiceBadge(current) : null;
          // The extra mark: a sent-back invoice of any month is in the list, and the row's own badge
          // does not already say so (never twice).
          const sentBackMark = sentBackContracts.has(c.id) && !isSentBackBadge(status);
          return { ...c, status, sentBackMark };
        })}
        selectedId={contractId}
        onSelect={select}
        // top-14 / 3.5rem is the 56px sticky top bar; a calc, not a token, because the column's
        // height is the viewport's minus that bar.
        className="hidden w-64 shrink-0 overflow-y-auto border-r border-hairline bg-canvas px-3 py-5 xl:sticky xl:top-14 xl:block xl:h-[calc(100dvh-3.5rem)]"
      />
      <div ref={invoiceAreaRef} className={cn(PAGE_PADDING, "xl:flex-1")}>
        <div className={cn(PAGE_WIDTH, "xl:mx-0")}>
          <div className="flex flex-col gap-4">
            <SentBackSection rows={sentBack} />
            <div className="xl:hidden">
              <ContractSwitcher contracts={switcherOptions} value={contractId} onChange={select} />
            </div>

            {!invoice ? (
              <EmptyState
                icon={<IconAlertTriangle className="h-5 w-5" />}
                title="Couldn't load this Contract's Client Invoice"
                description="Pick another Client or reload the page to try again."
              />
            ) : (
              <>
                {invoice.status === "DRAFT" && invoice.sentBackAt ? (
                  <SentBackNotice sentBackAt={invoice.sentBackAt} reason={invoice.sentBackReason} />
                ) : null}
                <AgentClientInvoiceCard invoice={invoice} />
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * The Manager's send-back as a warning-toned notice: when, the reason, and what to do next. Shown
 * on the by-id page and above a sent-back current-month card.
 */
function SentBackNotice({ sentBackAt, reason }: { sentBackAt: string; reason?: string | null }) {
  return (
    <section
      aria-labelledby="sent-back-notice"
      className="flex flex-col gap-1.5 rounded-lg border border-hairline bg-warning-bg px-4 py-3"
    >
      <h2 id="sent-back-notice" className="flex items-center gap-1.5 text-label font-semibold text-warning">
        <IconAlertTriangle className="h-4 w-4 shrink-0" />
        Sent back by the Manager on {formatDate(sentBackAt)}
      </h2>
      {reason ? <p className="whitespace-pre-wrap break-words text-label text-ink">{reason}</p> : null}
      <p className="text-label text-ink-secondary">Correct what is needed, then send it again.</p>
    </section>
  );
}

/** "Sent back to you": every invoice the Manager sent back, any month; renders nothing when none. */
function SentBackSection({ rows }: { rows: SentBackClientInvoice[] }) {
  if (rows.length === 0) return null;
  return (
    <section aria-labelledby="sent-back-to-you">
      <Card className="overflow-hidden">
        <h2 id="sent-back-to-you" className="px-4 py-3 text-sm font-semibold text-ink">
          Sent back to you
        </h2>
        <TableScroll className="rounded-none border-0 border-t">
          <Table>
            <Thead>
              <Tr>
                <Th>Contract</Th>
                <Th>Billing month</Th>
                <Th>Sent back</Th>
                <Th>Reason</Th>
                {/* relative: keeps the sr-only label inside the table's scroll container on narrow screens */}
                <Th className="relative text-right">
                  <span className="sr-only">Action</span>
                </Th>
              </Tr>
            </Thead>
            <Tbody>
              {rows.map((row) => {
                const month = formatBillingMonth(row.billingMonth);
                const contractLabel = `${row.clientName} — ${countryLabel(row.country)}`;
                return (
                  <Tr key={row.id}>
                    <Td className="min-w-32 max-w-56 font-medium text-ink">
                      <span className="line-clamp-2 break-words" title={contractLabel}>
                        {contractLabel}
                      </span>
                    </Td>
                    <Td className="whitespace-nowrap text-ink-secondary">{month}</Td>
                    <Td className="whitespace-nowrap text-ink-mute">{formatDate(row.sentBackAt)}</Td>
                    <Td className="min-w-56 max-w-md">
                      <p className="line-clamp-2 break-words text-ink-secondary" title={row.sentBackReason ?? undefined}>
                        {row.sentBackReason}
                      </p>
                    </Td>
                    <Td className="text-right">
                      <Link
                        href={`/agent/client-invoices/${row.id}`}
                        aria-label={`Open ${contractLabel}, ${month}`}
                        className="inline-flex h-7 items-center gap-1.5 whitespace-nowrap rounded-lg bg-primary-soft-bg px-3 text-label font-medium text-primary-soft-text transition-colors hover:bg-primary/20 active:bg-primary/25"
                      >
                        Open
                        <IconArrowRight className="h-3.5 w-3.5" />
                      </Link>
                    </Td>
                  </Tr>
                );
              })}
            </Tbody>
          </Table>
        </TableScroll>
      </Card>
    </section>
  );
}

/**
 * One Client Invoice on its own page, `/agent/client-invoices/{invoiceId}` (send-a-client-invoice-back
 * spec, Frontend: Agent): any billing month, reached by id. A draft the Manager sent back opens with
 * the Manager's reason in a warning notice above the same card the current-month page uses; a draft of
 * a past month that was never sent is shown read-only, because the backend refuses the Agent's writes
 * to it. `invoice` is null for another Agent's id, an unknown id and another Tenant's id alike.
 * `currentBillingMonth` comes from the server so server and client agree on the month.
 */
export function AgentClientInvoicePageView({
  invoice,
  currentBillingMonth,
  contractLabel,
}: {
  invoice: ClientInvoiceDetail | null;
  currentBillingMonth: string;
  /** "Client — Country" of the invoice's Contract, shown in the breadcrumb; absent if not resolved. */
  contractLabel?: string;
}) {
  if (!invoice) {
    return (
      <div className="flex flex-col gap-4">
        <Breadcrumb items={[{ label: "Client Invoices", href: "/agent/client-invoices" }, { label: "Not found" }]} />
        <EmptyState
          icon={<IconAlertTriangle className="h-5 w-5" />}
          title="Client Invoice not found"
          description="It doesn't exist or isn't one of yours."
          action={
            <Link href="/agent/client-invoices" className="text-label font-medium text-primary hover:underline">
              Back to Client Invoices
            </Link>
          }
        />
      </div>
    );
  }

  const sentBack = invoice.status === "DRAFT" && Boolean(invoice.sentBackAt);
  const closed = invoice.status === "DRAFT" && !sentBack && invoice.billingMonth < currentBillingMonth;

  return (
    <div className="flex flex-col gap-4">
      <Breadcrumb
        items={[
          { label: "Client Invoices", href: `/agent/client-invoices?contract=${encodeURIComponent(invoice.contractId)}` },
          {
            label: contractLabel
              ? `${contractLabel} · ${formatBillingMonth(invoice.billingMonth)}`
              : formatBillingMonth(invoice.billingMonth),
          },
        ]}
      />
      {sentBack && invoice.sentBackAt ? (
        <SentBackNotice sentBackAt={invoice.sentBackAt} reason={invoice.sentBackReason} />
      ) : null}
      <AgentClientInvoiceCard
        invoice={invoice}
        readOnly={closed}
        currentMonth={invoice.billingMonth === currentBillingMonth}
      />
    </div>
  );
}
