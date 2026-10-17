import { CarrierInvoiceCheck } from "@/components/ui/carrier-invoice-check";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { EditedLineNote } from "@/components/ui/billed-amount";
import { Money } from "@/components/ui/money";
import { IconDownload } from "@/components/icons";
import { CarrierInvoiceFilesSection } from "@/components/ui/carrier-invoice-files-section";
import { RemoveCarrierInvoiceFileControl } from "@/components/agent/remove-carrier-invoice-file-control";
import { AttachCarrierInvoiceFileControl } from "@/components/agent/attach-carrier-invoice-file-control";
import { EditClientInvoiceLineControl } from "@/components/agent/edit-client-invoice-line-control";
import { LINE_AREA, LINE_WRAPPER, LineCell, LineRow, LineTable } from "@/components/agent/invoice-line-table";
import { SendClientInvoiceControl } from "@/components/agent/send-client-invoice-control";
import { ClientInvoiceStatusNote } from "@/components/agent/client-invoice-status-note";
import { clientInvoiceBadge } from "@/lib/status";
import { formatDate, formatDateShort, formatLocalDate } from "@/lib/format";
import {
  FEE_TYPE_LABEL,
  missingCarrierInvoiceLines,
  simLineKind,
  type ClientInvoiceDetail,
  type EditableClientInvoiceLineKind,
} from "@/lib/api/types";

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

/** Whether a line shows the "Edited · computed" note (and, on a draft, Reset). */
function isEditedLine(line: { edited?: boolean | null; computedAmount?: number | null }): boolean {
  return Boolean(line.edited) && line.computedAmount != null;
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
  const isEdited = isEditedLine({ edited, computedAmount });
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
  // Composed here rather than through `BilledAmount` (which the Manager's view shares) so that below
  // `xl` the note can move into the row's own details area.
  return (
    <div className={LINE_WRAPPER}>
      <Money amount={amount} currency={currency} className={LINE_AREA.amount} />
      {isEdited ? (
        <div className={LINE_AREA.edited}>
          <EditedLineNote computedAmount={computedAmount ?? amount} currency={currency} />
        </div>
      ) : null}
    </div>
  );
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
  currentMonth = true,
}: {
  invoice: ClientInvoiceDetail;
  /** A past-month draft never sent: shown as it stands, with no editor, Attach file or Send. */
  readOnly?: boolean;
  /** False for an invoice of another billing month: its PDF isn't served by id, and "this month" is wrong. */
  currentMonth?: boolean;
}) {
  const editable = invoice.status === "DRAFT" && !readOnly;
  const badge = clientInvoiceBadge(invoice);
  const feeLabelList = feeLabels(invoice.feeLines);
  const hasPostpaidLines = (invoice.basePostpaidSims?.length ?? 0) > 0;

  return (
    <Card className="flex flex-col gap-6 p-5" data-testid="client-invoice-card">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <p className="text-sm font-semibold text-ink">{billingMonthLabel(invoice.billingMonth)}</p>
            <Badge tone={badge.tone}>{badge.label}</Badge>
          </div>
          <ClientInvoiceStatusNote status={invoice.status} className="mt-1 text-label text-ink-mute">
            {readOnly && invoice.status === "DRAFT"
              ? "This month has ended and the invoice was never sent — it is shown as it stands and is closed to changes"
              : invoice.status === "DRAFT"
              ? currentMonth
                ? "This Contract’s postpaid base amount plus this month’s Fees — still being assembled"
                : "This Contract’s postpaid base amount plus that month’s Fees — open to your corrections"
              : "Sent to the Manager and Client — numbers are locked to what was sent"}
          </ClientInvoiceStatusNote>
          {invoice.sentAt ? (
            <p className="mt-0.5 text-label-sm text-ink-mute">
              Sent {formatDate(invoice.sentAt)}
              {invoice.approvedAt ? ` · Approved ${formatDate(invoice.approvedAt)}` : ""}
            </p>
          ) : null}
        </div>
        {editable ? (
          <div className="flex flex-col items-end gap-2">
            {hasPostpaidLines ? (
              <AttachCarrierInvoiceFileControl
                invoiceId={invoice.id}
                missingLines={missingCarrierInvoiceLines(invoice)}
                disabled={!invoice.filesAvailable}
              />
            ) : null}
            <SendClientInvoiceControl
              invoiceId={invoice.id}
              missingCarrierInvoices={missingCarrierInvoiceLines(invoice).length}
            />
          </div>
        ) : invoice.status === "DRAFT" || !currentMonth ? null : (
          <a
            href={`/api/contracts/${invoice.contractId}/client-invoice/pdf`}
            className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-hairline-strong bg-canvas px-3 py-1.5 text-label font-medium text-ink hover:bg-canvas-soft"
          >
            <IconDownload className="h-3.5 w-3.5" />
            Download PDF
          </a>
        )}
      </div>

      <dl className="grid grid-cols-2 gap-4 rounded-lg border border-hairline bg-canvas-soft p-4 sm:grid-cols-3">
        <div>
          <dt className="text-label-sm font-medium uppercase tracking-wide text-ink-mute">Base amount</dt>
          <dd className="mt-1">
            <Money amount={invoice.baseAmount} currency={invoice.currency} className="text-base" />
          </dd>
        </div>
        <div>
          <dt className="text-label-sm font-medium uppercase tracking-wide text-ink-mute">Fees this month</dt>
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

      {editable ? (
        <p className="-mt-2 text-label text-ink-mute">
          You can adjust any line to what was actually billed. Your Agent Invoice for this month follows these
          amounts, unless it is already approved.
        </p>
      ) : null}

      {invoice.basePostpaidSims && invoice.basePostpaidSims.length > 0 ? (
        <div>
          <h3 className="mb-2 text-label font-medium text-ink-secondary">Postpaid SIM Cards</h3>
          <LineTable columns={[{ label: "Number" }, { label: "Billed", align: "right" }]} withActions={editable}>
            {invoice.basePostpaidSims.map((sim) => (
              <LineRow key={sim.simCardId} edited={isEditedLine(sim)}>
                <LineCell className="tnum font-medium">
                  <span className={`wrap-anywhere ${LINE_AREA.what}`}>
                    <span className="mr-2">{sim.number}</span>
                    <span className="inline-block whitespace-nowrap text-label-sm font-normal text-ink-mute">
                      {simLineKind(sim)}
                    </span>
                    <CarrierInvoiceCheck carrierInvoiceFileId={sim.carrierInvoiceFileId} />
                  </span>
                  {sim.cancellationEffectiveDate ? (
                    <span className={`mt-1 block text-label-sm font-normal text-ink-mute ${LINE_AREA.note}`}>
                      Cancelled {formatLocalDate(sim.cancellationEffectiveDate)} — still billed through this month
                    </span>
                  ) : null}
                </LineCell>
                <LineCell className="text-right">
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
                </LineCell>
              </LineRow>
            ))}
          </LineTable>
        </div>
      ) : null}

      <div>
        <h3 className="mb-2 text-label font-medium text-ink-secondary">Fee lines</h3>
        {invoice.feeLines.length === 0 ? (
          <p className="rounded-lg border border-dashed border-hairline-strong px-4 py-6 text-center text-label text-ink-mute">
            No Fees logged against this Contract yet this month.
          </p>
        ) : (
          <LineTable
            columns={[
              { label: "Type" },
              { label: "Description" },
              { label: "Logged" },
              { label: "Billed", align: "right" },
            ]}
            withActions={editable}
          >
            {invoice.feeLines.map((fee, index) => (
              <LineRow key={fee.id} edited={isEditedLine(fee)}>
                <LineCell className="font-medium">
                  <span className={`wrap-anywhere ${LINE_AREA.what}`}>{FEE_TYPE_LABEL[fee.feeType]}</span>
                </LineCell>
                <LineCell className="text-ink-secondary">
                  {/* The dash is for the table's Description column; below `xl` a missing description has no area at all. */}
                  {fee.description ? (
                    <span className={`wrap-anywhere ${LINE_AREA.desc}`}>{fee.description}</span>
                  ) : (
                    <span className="max-xl:hidden">—</span>
                  )}
                </LineCell>
                <LineCell className="whitespace-nowrap text-ink-mute max-xl:text-label-sm">
                  {/* The column header names the date at `xl`; below it the headers are hidden, so the row says "Logged". */}
                  <span className={LINE_AREA.note}>
                    <span className="xl:hidden">Logged </span>
                    {formatDateShort(fee.createdAt)}
                  </span>
                </LineCell>
                <LineCell className="text-right">
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
                </LineCell>
              </LineRow>
            ))}
          </LineTable>
        )}
      </div>

      <CarrierInvoiceFilesSection
        files={invoice.files}
        downloadHref={(fileId) => `/api/client-invoices/${invoice.id}/files/${fileId}`}
        emptyText="No carrier invoice files attached yet."
        filesAvailable={invoice.filesAvailable}
        canAttach={editable}
        renderAction={
          editable
            ? (file) => (
                <RemoveCarrierInvoiceFileControl
                  invoiceId={invoice.id}
                  fileId={file.id}
                  filename={file.filename}
                  disabled={!invoice.filesAvailable}
                />
              )
            : undefined
        }
      />
    </Card>
  );
}
