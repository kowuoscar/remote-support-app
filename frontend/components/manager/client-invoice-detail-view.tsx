"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Money } from "@/components/ui/money";
import { IconDownload, IconPaperclip } from "@/components/icons";
import { ApproveClientInvoiceControl } from "@/components/manager/approve-client-invoice-control";
import { clientInvoiceStatusLabelByValue, clientInvoiceStatusToneByValue } from "@/lib/status";
import { formatBillingMonth, formatDate } from "@/lib/format";
import { FEE_TYPE_LABEL, type ClientInvoiceDetail } from "@/lib/api/types";

function statusNote(invoice: ClientInvoiceDetail): string {
  switch (invoice.status) {
    case "DRAFT":
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
          <p className="mt-1 text-[13px] text-ink-mute">{statusNote(invoice)}</p>
          {invoice.sentAt ? (
            <p className="mt-0.5 text-[12px] text-ink-mute">
              Sent {formatDate(invoice.sentAt)}
              {invoice.approvedAt ? ` · Approved ${formatDate(invoice.approvedAt)}` : ""}
            </p>
          ) : null}
        </div>
        <div className="flex flex-wrap items-start gap-2">
          {invoice.status !== "DRAFT" ? (
            <a
              href={`${base}/pdf`}
              className="inline-flex h-7 shrink-0 items-center gap-1.5 rounded-lg border border-hairline-strong bg-canvas px-3 text-[13px] font-medium text-ink hover:bg-canvas-soft"
            >
              <IconDownload className="h-3.5 w-3.5" />
              Download PDF
            </a>
          ) : null}
          {invoice.status === "SENT" ? (
            <ApproveClientInvoiceControl invoiceId={invoice.id} onApproved={setInvoice} />
          ) : null}
        </div>
      </div>

      <dl className="grid grid-cols-2 gap-4 rounded-lg border border-hairline bg-canvas-soft p-4 sm:grid-cols-3">
        <div>
          <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Base amount</dt>
          <dd className="mt-1">
            <Money amount={invoice.baseAmount} currency={invoice.currency} className="text-base" />
          </dd>
        </div>
        <div>
          <dt className="text-[12px] font-medium uppercase tracking-wide text-ink-mute">Fees</dt>
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
        <h3 className="mb-2 text-[13px] font-medium text-ink-secondary">Fee lines</h3>
        {invoice.feeLines.length === 0 ? (
          <p className="rounded-lg border border-dashed border-hairline-strong px-4 py-6 text-center text-[13px] text-ink-mute">
            No Fees on this invoice.
          </p>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {invoice.feeLines.map((fee) => (
              <li
                key={fee.id}
                className="flex items-center justify-between gap-3 rounded-lg border border-hairline bg-canvas px-3.5 py-2.5 text-[13px]"
              >
                <span className="min-w-0 text-ink">
                  {FEE_TYPE_LABEL[fee.feeType]}
                  {fee.description ? <span className="text-ink-mute"> — {fee.description}</span> : null}
                </span>
                <Money amount={fee.amount} currency={fee.currency} />
              </li>
            ))}
          </ul>
        )}
      </div>

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
                  href={`${base}/files/${file.id}`}
                  aria-label={`Download ${file.filename}`}
                  className="inline-flex shrink-0 items-center gap-1 text-[12px] font-medium text-primary underline-offset-2 hover:underline"
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
