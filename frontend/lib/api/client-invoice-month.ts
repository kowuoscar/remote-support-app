import { missingCarrierInvoiceLines, type ClientInvoiceDetail } from "@/lib/api/types";

/**
 * The Agent's month as the Client Invoices page can see it (agent-sends-the-month): derived from
 * the invoices the page already loaded, with no read of its own. `billingMonth` is the first-of-month
 * ISO date of the loaded invoices, `null` only when no invoice loaded at all.
 */
export type ClientInvoiceMonthState =
  | { kind: "unsent"; billingMonth: string; total: number; ready: number; invoiceIds: string[] }
  | { kind: "sent"; billingMonth: string; sentAt: string }
  | { kind: "incomplete"; billingMonth: string | null };

/**
 * `sent` when any loaded invoice carries `monthSentAt`; `incomplete` when a Contract's invoice
 * failed to load (a month the page could not fully see must not be sent from it); else `unsent`,
 * with `ready` counting the `DRAFT` invoices holding no Postpaid line without its carrier invoice.
 * `null` when the Agent holds no Contract: there is no month to show.
 */
export function clientInvoiceMonth(invoices: (ClientInvoiceDetail | null)[]): ClientInvoiceMonthState | null {
  if (invoices.length === 0) return null;
  const loaded = invoices.filter((invoice): invoice is ClientInvoiceDetail => invoice !== null);
  const billingMonth = loaded[0]?.billingMonth ?? null;
  const sentAt = loaded.find((invoice) => invoice.monthSentAt)?.monthSentAt;
  if (billingMonth && sentAt) return { kind: "sent", billingMonth, sentAt };
  if (!billingMonth || loaded.length < invoices.length) return { kind: "incomplete", billingMonth };
  return {
    kind: "unsent",
    billingMonth,
    total: loaded.length,
    ready: loaded.filter((invoice) => invoice.status === "DRAFT" && missingCarrierInvoiceLines(invoice).length === 0)
      .length,
    invoiceIds: loaded.map((invoice) => invoice.id),
  };
}
