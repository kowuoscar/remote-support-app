"use client";

import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { BilledAmount } from "@/components/ui/billed-amount";
import { Money } from "@/components/ui/money";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconDownload, IconPaperclip } from "@/components/icons";
import { AttachCarrierInvoiceFileControl } from "@/components/agent/attach-carrier-invoice-file-control";
import { EditClientInvoiceLineControl } from "@/components/agent/edit-client-invoice-line-control";
import { SendClientInvoiceControl } from "@/components/agent/send-client-invoice-control";
import { clientInvoiceBadge } from "@/lib/status";
import { formatDate, formatDateShort, formatLocalDate } from "@/lib/format";
import { FEE_TYPE_LABEL, type ClientInvoiceDetail, type EditableClientInvoiceLineKind } from "@/lib/api/types";

const FEE_LABEL_DESCRIPTION_MAX = 40;

/**
 * Names each Fee line for assistive tech: type, day and a shortened description; Fees that would
 * still share a name (same type and day, no or equal description) get their ordinal among them.
 */
function feeLabels(fees: ClientInvoiceDetail["feeLines"]): string[] {
  const base = fees.map((fee) => {
    const description = fee.description?.trim();
    const shown =
      description && description.length > FEE_LABEL_DESCRIPTION_MAX
        ? `${description.slice(0, FEE_LABEL_DESCRIPTION_MAX - 1).trimEnd()}…`
        : description;
    return `${FEE_TYPE_LABEL[fee.feeType]} Fee, ${formatDateShort(fee.createdAt)}${shown ? ` — ${shown}` : ""}`;
  });
  const seen = new Map<string, number>();
  return base.map((label) => {
    const total = base.filter((other) => other === label).length;
    if (total === 1) return label;
    const ordinal = (seen.get(label) ?? 0) + 1;
    seen.set(label, ordinal);
    return `${label} (${ordinal} of ${total})`;
  });
}

function billingMonthLabel(billingMonth: string): string {
  // billingMonth is always a first-of-month ISO date (e.g. "2026-09-01") — parsed as UTC so it
  // never rolls back to the previous month in a timezone behind UTC.
  return new Date(`${billingMonth}T00:00:00Z`).toLocaleDateString("en-US", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

/**
 * One line's amount: with Edit/Reset on a draft, otherwise the billed amount alone, plus the
 * "Edited · computed" note when the line was edited (a sent invoice keeps showing it).
 */
function LineAmount({
  editable,
  invoiceId,
  kind,
  sourceId,
  label,
  amount,
  computedAmount,
  edited,
  currency,
}: {
  editable: boolean;
  invoiceId: string;
  kind: EditableClientInvoiceLineKind;
  sourceId: string;
  label: string;
  amount: number;
  computedAmount: number | null | undefined;
  edited: boolean | null | undefined;
  currency: string;
}) {
  const isEdited = Boolean(edited) && computedAmount != null;
  if (editable) {
    return (
      <EditClientInvoiceLineControl
        invoiceId={invoiceId}
        kind={kind}
        sourceId={sourceId}
        label={label}
        amount={amount}
        computedAmount={computedAmount ?? amount}
        edited={isEdited}
        currency={currency}
      />
    );
  }
  return <BilledAmount amount={amount} computedAmount={computedAmount} edited={isEdited} currency={currency} />;
}

/**
 * One Client Invoice as the Agent sees it (client-invoice-generation ticket AC: "The draft view is
 * scannable at a glance: base amount, each Fee line, and attached files are all visible
 * together"): the summary row (base/fees/total) is the "at a glance" part, with Fee lines and
 * files as simple lists below. Its controls address the invoice by its own id, so the same card
 * serves the current-month page and an invoice of any other billing month.
 */
export function AgentClientInvoiceCard({
  invoice,
  readOnly = false,
}: {
  invoice: ClientInvoiceDetail;
  /** A past-month draft never sent: shown as it stands, with no editor, Attach file or Send. */
  readOnly?: boolean;
}) {
  const editable = invoice.status === "DRAFT" && !readOnly;
  const badge = clientInvoiceBadge(invoice);
  const feeLabelList = feeLabels(invoice.feeLines);

  return (
    <Card className="flex flex-col gap-6 p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <p className="text-sm font-semibold text-ink">{billingMonthLabel(invoice.billingMonth)}</p>
            <Badge tone={badge.tone}>{badge.label}</Badge>
          </div>
          <p className="mt-1 text-[13px] text-ink-mute">
            {readOnly && invoice.status === "DRAFT"
              ? "This month has ended and the invoice was never sent — it is shown as it stands and is closed to changes"
              : invoice.status === "DRAFT"
              ? "This Contract’s postpaid base amount plus this month’s Fees — still being assembled"
              : "Sent to the Manager and Client — numbers are locked to what was sent"}
          </p>
          {invoice.sentAt ? (
            <p className="mt-0.5 text-[12px] text-ink-mute">
              Sent {formatDate(invoice.sentAt)}
              {invoice.approvedAt ? ` · Approved ${formatDate(invoice.approvedAt)}` : ""}
            </p>
          ) : null}
        </div>
        {editable ? (
          <div className="flex flex-col items-end gap-2">
            <AttachCarrierInvoiceFileControl invoiceId={invoice.id} />
            <SendClientInvoiceControl invoiceId={invoice.id} />
          </div>
        ) : invoice.status === "DRAFT" ? null : (
          <a
            href={`/api/contracts/${invoice.contractId}/client-invoice/pdf`}
            className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-hairline-strong bg-canvas px-3 py-1.5 text-[13px] font-medium text-ink hover:bg-canvas-soft"
          >
            <IconDownload className="h-3.5 w-3.5" />
            Download PDF
          </a>
        )}
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

      {editable ? (
        <p className="-mt-2 text-label text-ink-mute">
          You can adjust any line to what was actually billed. Your Agent Invoice for this month follows these
          amounts, unless it is already approved.
        </p>
      ) : null}

      {invoice.basePostpaidSims && invoice.basePostpaidSims.length > 0 ? (
        <div>
          <h3 className="mb-2 text-[13px] font-medium text-ink-secondary">Postpaid SIM Cards</h3>
          <TableScroll>
            <Table>
              <Thead>
                <Tr>
                  <Th>Number</Th>
                  <Th className="text-right">Billed</Th>
                </Tr>
              </Thead>
              <Tbody>
                {invoice.basePostpaidSims.map((sim) => (
                  <Tr key={sim.simCardId}>
                    <Td className="tnum font-medium text-ink">
                      {sim.number}
                      {sim.cancellationEffectiveDate ? (
                        <span className="mt-1 block text-[12px] font-normal text-ink-mute">
                          Cancelled {formatLocalDate(sim.cancellationEffectiveDate)} — still billed through this
                          month
                        </span>
                      ) : null}
                    </Td>
                    <Td className="text-right">
                      {/* An edited line bills its own amount, not the SIM's monthly fee. Older fixtures omit `amount`. */}
                      <LineAmount
                        editable={editable}
                        invoiceId={invoice.id}
                        kind="POSTPAID_SIM"
                        sourceId={sim.simCardId}
                        label={`SIM ${sim.number}`}
                        amount={sim.amount ?? sim.monthlyFeeAmount}
                        computedAmount={sim.computedAmount}
                        edited={sim.edited}
                        currency={invoice.currency}
                      />
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          </TableScroll>
        </div>
      ) : null}

      <div>
        <h3 className="mb-2 text-[13px] font-medium text-ink-secondary">Fee lines</h3>
        {invoice.feeLines.length === 0 ? (
          <p className="rounded-lg border border-dashed border-hairline-strong px-4 py-6 text-center text-[13px] text-ink-mute">
            No Fees logged against this Contract yet this month.
          </p>
        ) : (
          <TableScroll>
            <Table>
              <Thead>
                <Tr>
                  <Th>Type</Th>
                  <Th>Description</Th>
                  <Th>Logged</Th>
                  <Th className="text-right">Billed</Th>
                </Tr>
              </Thead>
              <Tbody>
                {invoice.feeLines.map((fee, index) => (
                  <Tr key={fee.id}>
                    <Td className="font-medium text-ink">{FEE_TYPE_LABEL[fee.feeType]}</Td>
                    <Td className="text-ink-secondary">{fee.description ?? "—"}</Td>
                    <Td className="whitespace-nowrap text-ink-mute">{formatDateShort(fee.createdAt)}</Td>
                    <Td className="text-right">
                      <LineAmount
                        editable={editable}
                        invoiceId={invoice.id}
                        kind="FEE"
                        sourceId={fee.id}
                        label={feeLabelList[index]}
                        amount={fee.amount}
                        computedAmount={fee.computedAmount}
                        edited={fee.edited}
                        currency={fee.currency}
                      />
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          </TableScroll>
        )}
      </div>

      <div>
        <h3 className="mb-2 flex items-center gap-1.5 text-[13px] font-medium text-ink-secondary">
          <IconPaperclip className="h-4 w-4" />
          Carrier invoice files
        </h3>
        {invoice.files.length === 0 ? (
          <p className="rounded-lg border border-dashed border-hairline-strong px-4 py-6 text-center text-[13px] text-ink-mute">
            No carrier invoice files attached yet.
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
                  href={`/api/client-invoices/${invoice.id}/files/${file.id}`}
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
    </Card>
  );
}
