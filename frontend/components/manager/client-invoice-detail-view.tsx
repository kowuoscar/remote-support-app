"use client";

import { useEffect, useRef, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { BilledAmount } from "@/components/ui/billed-amount";
import { Money } from "@/components/ui/money";
import { IconDownload, IconPaperclip } from "@/components/icons";
import { ApproveClientInvoiceControl } from "@/components/manager/approve-client-invoice-control";
import { SendBackClientInvoiceControl } from "@/components/manager/send-back-client-invoice-control";
import { clientInvoiceStatusLabelByValue, clientInvoiceStatusToneByValue } from "@/lib/status";
import { formatBillingMonth, formatDate, formatLocalDate } from "@/lib/format";
import { FEE_TYPE_LABEL, type ClientInvoiceDetail } from "@/lib/api/types";

function statusText(invoice: ClientInvoiceDetail): string {
  switch (invoice.status) {
    case "DRAFT":
      if (invoice.sentBackAt) {
        return `Sent back to the Agent on ${formatDate(invoice.sentBackAt)} — waiting for them to resend`;
      }
      return "Still being assembled by the Agent — nothing to review yet";
    case "SENT":
      return "Base amount and Fee lines are locked to what the Agent sent";
    case "APPROVED":
      return "Approved — this is the final record for the month";
  }
}

/**
 * One Client Invoice, in full, for the Manager's review (manager-invoice-review-queue spec, Client
 * Invoice detail page): base amount, Fee lines, total, status and timestamps, its Carrier Invoice
 * Files and PDF, all addressed by the invoice's own id so any billing month works. Approve shows
 * only while `sent`; every other status renders read-only. A successful approval replaces the
 * invoice shown here with the approved one the backend returns, so the final state appears in
 * place without navigating.
 */
export function ClientInvoiceDetailView({ invoice: initial }: { invoice: ClientInvoiceDetail }) {
  const [invoice, setInvoice] = useState(initial);
  const base = `/api/client-invoices/${invoice.id}`;
  const [sendingBack, setSendingBack] = useState(false);
  const statusNote = useRef<HTMLParagraphElement>(null);
  const previousStatus = useRef(initial.status);

  // A send-back or approval swaps the controls the Manager was on for the new state, so the status
  // note takes focus (tabIndex -1) instead of focus falling to the page; first render never does.
  useEffect(() => {
    if (previousStatus.current !== invoice.status) {
      previousStatus.current = invoice.status;
      statusNote.current?.focus();
    }
  }, [invoice.status]);

  return (
    <Card className="flex flex-col gap-6 p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-base font-semibold text-ink">{formatBillingMonth(invoice.billingMonth)}</h2>
            <Badge tone={clientInvoiceStatusToneByValue[invoice.status]}>
              {clientInvoiceStatusLabelByValue[invoice.status]}
            </Badge>
          </div>
          <p ref={statusNote} tabIndex={-1} className="mt-1 text-label text-ink-mute outline-none">
            {statusText(invoice)}
          </p>
          {invoice.sentAt ? (
            <p className="mt-0.5 text-label-sm text-ink-mute">
              Sent {formatDate(invoice.sentAt)}
              {invoice.approvedAt ? ` · Approved ${formatDate(invoice.approvedAt)}` : ""}
            </p>
          ) : null}
          {invoice.status === "SENT" && invoice.sentBackAt ? (
            <p className="mt-0.5 text-label-sm text-ink-mute">
              Previously sent back on {formatDate(invoice.sentBackAt)}: {invoice.sentBackReason}
            </p>
          ) : null}
        </div>
        <div className={`flex flex-wrap items-start gap-2 ${sendingBack ? "w-full" : ""}`}>
          {invoice.status !== "DRAFT" ? (
            <a
              href={`${base}/pdf`}
              className="inline-flex h-7 shrink-0 items-center gap-1.5 rounded-lg border border-hairline-strong bg-canvas px-3 text-label font-medium text-ink hover:bg-canvas-soft"
            >
              <IconDownload className="h-3.5 w-3.5" />
              Download PDF
            </a>
          ) : null}
          {invoice.status === "SENT" ? (
            <>
              <SendBackClientInvoiceControl
                endpoint={`${base}/send-back`}
                onSentBack={setInvoice}
                onOpenChange={setSendingBack}
              />
              {sendingBack ? null : <ApproveClientInvoiceControl invoiceId={invoice.id} onApproved={setInvoice} />}
            </>
          ) : null}
        </div>
      </div>

      {invoice.status === "DRAFT" && invoice.sentBackAt && invoice.sentBackReason ? (
        <blockquote className="rounded-lg border border-hairline bg-canvas-soft px-4 py-3 text-label text-ink">
          <p className="whitespace-pre-wrap break-words">{invoice.sentBackReason}</p>
        </blockquote>
      ) : null}

      <dl className="grid grid-cols-2 gap-4 rounded-lg border border-hairline bg-canvas-soft p-4 sm:grid-cols-3">
        <div>
          <dt className="text-label-sm font-medium uppercase tracking-wide text-ink-mute">Base amount</dt>
          <dd className="mt-1">
            <Money amount={invoice.baseAmount} currency={invoice.currency} className="text-base" />
          </dd>
        </div>
        <div>
          <dt className="text-label-sm font-medium uppercase tracking-wide text-ink-mute">Fees</dt>
          <dd className="mt-1">
            <Money
              amount={invoice.totalAmount - invoice.baseAmount}
              currency={invoice.currency}
              className="text-base"
            />
          </dd>
        </div>
        <div>
          <dt className="text-label-sm font-medium uppercase tracking-wide text-ink-mute">Total</dt>
          <dd className="mt-1">
            <Money amount={invoice.totalAmount} currency={invoice.currency} emphasize className="text-lg" />
          </dd>
        </div>
      </dl>

      {invoice.basePostpaidSims && invoice.basePostpaidSims.length > 0 ? (
        <div>
          <h3 id="postpaid-sim-lines" className="mb-2 text-label font-medium text-ink-secondary">
            Postpaid SIM Cards
          </h3>
          <ul aria-labelledby="postpaid-sim-lines" className="flex flex-col gap-1.5">
            {invoice.basePostpaidSims.map((sim) => (
              <li
                key={sim.simCardId}
                className="flex items-start justify-between gap-3 rounded-lg border border-hairline bg-canvas px-3.5 py-2.5 text-label"
              >
                <span className="min-w-0 break-words text-ink">
                  <span className="tnum font-medium">{sim.number}</span>
                  {sim.cancellationEffectiveDate ? (
                    <span className="block text-label-sm text-ink-mute">
                      Cancelled {formatLocalDate(sim.cancellationEffectiveDate)} — still billed through this month
                    </span>
                  ) : null}
                </span>
                <BilledAmount
                  amount={sim.amount ?? sim.monthlyFeeAmount}
                  computedAmount={sim.computedAmount}
                  edited={sim.edited}
                  currency={invoice.currency}
                />
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <div>
        <h3 className="mb-2 text-label font-medium text-ink-secondary">Fee lines</h3>
        {invoice.feeLines.length === 0 ? (
          <p className="rounded-lg border border-dashed border-hairline-strong px-4 py-6 text-center text-label text-ink-mute">
            No Fees on this invoice.
          </p>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {invoice.feeLines.map((fee) => (
              <li
                key={fee.id}
                className="flex items-start justify-between gap-3 rounded-lg border border-hairline bg-canvas px-3.5 py-2.5 text-label"
              >
                <span className="min-w-0 text-ink">
                  {FEE_TYPE_LABEL[fee.feeType]}
                  {fee.description ? <span className="text-ink-mute"> — {fee.description}</span> : null}
                </span>
                <BilledAmount
                  amount={fee.amount}
                  computedAmount={fee.computedAmount}
                  edited={fee.edited}
                  currency={fee.currency}
                />
              </li>
            ))}
          </ul>
        )}
      </div>

      <div>
        <h3 className="mb-2 flex items-center gap-1.5 text-label font-medium text-ink-secondary">
          <IconPaperclip className="h-4 w-4" />
          Carrier invoice files
        </h3>
        {invoice.files.length === 0 ? (
          <p className="rounded-lg border border-dashed border-hairline-strong px-4 py-6 text-center text-label text-ink-mute">
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
                  <span className="truncate text-label text-ink">{file.filename}</span>
                </div>
                <a
                  href={`${base}/files/${file.id}`}
                  aria-label={`Download ${file.filename}`}
                  className="inline-flex shrink-0 items-center gap-1 text-label-sm font-medium text-primary underline-offset-2 hover:underline"
                >
                  <IconDownload className="h-3.5 w-3.5" />
                  Download
                </a>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}
