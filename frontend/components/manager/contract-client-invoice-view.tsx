import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Money } from "@/components/ui/money";
import { IconDownload, IconInvoices, IconPaperclip } from "@/components/icons";
import { ApproveClientInvoiceControl } from "@/components/manager/approve-client-invoice-control";
import { clientInvoiceStatusLabelByValue, clientInvoiceStatusToneByValue } from "@/lib/status";
import { formatDate } from "@/lib/format";
import { FEE_TYPE_LABEL, type ClientInvoiceDetail } from "@/lib/api/types";

function billingMonthLabel(billingMonth: string): string {
  // billingMonth is always a first-of-month ISO date — parsed as UTC so it never rolls back to
  // the previous month in a timezone behind UTC (mirrors AgentClientInvoicesView's helper).
  return new Date(`${billingMonth}T00:00:00Z`).toLocaleDateString("en-US", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

/**
 * A Contract's current-month Client Invoice, for the Manager's review (ticket AC: "Manager can
 * review a sent Client Invoice, including its attached carrier files, and approve it"). Surfaces
 * on the Contract detail page — there's no dedicated Manager Client Invoice view yet — the same
 * "detail-view CRUD/review scoped to one owning record" shape ManagerContractFleetView already
 * established for this same page. A Manager can already view a `draft` invoice too (spec.md
 * Access control: Manager oversight parity), shown here read-only with no Approve action, since
 * only a `sent` invoice is ready for that (ticket AC: "Manager cannot approve a Client Invoice
 * still in draft").
 */
export function ManagerContractClientInvoiceView({
  contractId,
  invoice,
}: {
  contractId: string;
  invoice: ClientInvoiceDetail | null;
}) {
  if (!invoice) {
    return null;
  }

  return (
    <Card className="flex flex-col gap-6 p-5">
      <div className="flex items-center gap-2 border-b border-hairline pb-3.5">
        <IconInvoices className="h-4 w-4 text-ink-mute" />
        <h2 className="text-sm font-semibold text-ink">Client Invoice</h2>
      </div>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <p className="text-sm font-semibold text-ink">{billingMonthLabel(invoice.billingMonth)}</p>
            <Badge tone={clientInvoiceStatusToneByValue[invoice.status]}>
              {clientInvoiceStatusLabelByValue[invoice.status]}
            </Badge>
          </div>
          <p className="mt-1 text-[13px] text-ink-mute">
            {invoice.status === "DRAFT"
              ? "Still being assembled by the Agent — nothing to review yet"
              : "Base amount and Fee lines are locked to what the Agent sent"}
          </p>
          {invoice.sentAt ? (
            <p className="mt-0.5 text-[12px] text-ink-mute">
              Sent {formatDate(invoice.sentAt)}
              {invoice.approvedAt ? ` · Approved ${formatDate(invoice.approvedAt)}` : ""}
            </p>
          ) : null}
        </div>
        {invoice.status === "SENT" ? <ApproveClientInvoiceControl contractId={contractId} /> : null}
        {invoice.status !== "DRAFT" ? (
          <a
            href={`/api/contracts/${contractId}/client-invoice/pdf`}
            className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-hairline-strong bg-canvas px-3 py-1.5 text-[13px] font-medium text-ink hover:bg-canvas-soft"
          >
            <IconDownload className="h-3.5 w-3.5" />
            Download PDF
          </a>
        ) : null}
      </div>

      <dl className="grid grid-cols-2 gap-4 rounded-lg border border-hairline bg-canvas-soft p-4 sm:grid-cols-3">
        <div>
          <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Base amount</dt>
          <dd className="mt-1">
            <Money amount={invoice.baseAmount} currency={invoice.currency} className="text-base" />
          </dd>
        </div>
        <div>
          <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Fees this month</dt>
          <dd className="mt-1">
            <Money
              amount={invoice.totalAmount - invoice.baseAmount}
              currency={invoice.currency}
              className="text-base"
            />
          </dd>
        </div>
        <div>
          <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Total</dt>
          <dd className="mt-1">
            <Money amount={invoice.totalAmount} currency={invoice.currency} emphasize className="text-lg" />
          </dd>
        </div>
      </dl>

      <div>
        <h3 className="mb-2 flex items-center gap-1.5 text-[13px] font-medium text-ink-secondary">
          <IconPaperclip className="h-4 w-4" />
          Carrier invoice files
        </h3>
        {invoice.files.length === 0 ? (
          <p className="rounded-lg border border-dashed border-hairline-strong px-4 py-6 text-center text-[13px] text-ink-mute">
            No carrier invoice files attached.
          </p>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {invoice.files.map((file) => (
              <li
                key={file.id}
                className="flex items-center justify-between gap-3 rounded-lg border border-hairline bg-canvas px-3.5 py-2.5"
              >
                <div className="flex min-w-0 items-center gap-2">
                  <IconPaperclip className="h-4 w-4 shrink-0 text-ink-mute" />
                  <span className="truncate text-[13px] text-ink">{file.filename}</span>
                </div>
                <a
                  href={`/api/contracts/${contractId}/client-invoice/files/${file.id}`}
                  className="inline-flex shrink-0 items-center gap-1 text-[12px] font-medium text-primary hover:underline"
                >
                  <IconDownload className="h-3.5 w-3.5" />
                  Download
                </a>
              </li>
            ))}
          </ul>
        )}
      </div>

      {invoice.feeLines.length > 0 ? (
        <div>
          <h3 className="mb-2 text-[13px] font-medium text-ink-secondary">Fee lines</h3>
          <ul className="flex flex-col gap-1.5">
            {invoice.feeLines.map((fee) => (
              <li
                key={fee.id}
                className="flex items-center justify-between gap-3 rounded-lg border border-hairline bg-canvas px-3.5 py-2.5 text-[13px]"
              >
                <span className="text-ink">
                  {FEE_TYPE_LABEL[fee.feeType]}
                  {fee.description ? <span className="text-ink-mute"> — {fee.description}</span> : null}
                </span>
                <Money amount={fee.amount} currency={fee.currency} />
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </Card>
  );
}
