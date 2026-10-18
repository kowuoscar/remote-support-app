import { describe, expect, it } from "vitest";
import { clientInvoiceMonth } from "@/lib/api/client-invoice-month";
import type { ClientInvoiceDetail } from "@/lib/api/types";

function invoice(overrides: Partial<ClientInvoiceDetail> = {}): ClientInvoiceDetail {
  return {
    id: "invoice-1",
    contractId: "contract-1",
    billingMonth: "2026-10-01",
    status: "DRAFT",
    currency: "USD",
    baseAmount: 0,
    basePostpaidSims: [],
    feeLines: [],
    totalAmount: 0,
    files: [],
    filesAvailable: true,
    sentAt: null,
    approvedAt: null,
    monthSentAt: null,
    sendableOnItsOwn: false,
    ...overrides,
  };
}

const MISSING_FILE = {
  simCardId: "sim-1",
  carrierName: "Safaricom",
  carrierInvoiceFileId: null,
} as unknown as NonNullable<ClientInvoiceDetail["basePostpaidSims"]>[number];
const ATTACHED = { ...MISSING_FILE, carrierInvoiceFileId: "file-1" };

describe("clientInvoiceMonth", () => {
  it.each([
    {
      name: "every draft ready",
      invoices: [invoice({ id: "a" }), invoice({ id: "b", basePostpaidSims: [ATTACHED] })],
      expected: { kind: "unsent", billingMonth: "2026-10-01", total: 2, ready: 2, invoiceIds: ["a", "b"] },
    },
    {
      name: "one draft missing a file",
      invoices: [invoice({ id: "a" }), invoice({ id: "b", basePostpaidSims: [ATTACHED, MISSING_FILE] })],
      expected: { kind: "unsent", billingMonth: "2026-10-01", total: 2, ready: 1, invoiceIds: ["a", "b"] },
    },
    {
      name: "one Contract",
      invoices: [invoice({ id: "a", basePostpaidSims: [MISSING_FILE] })],
      expected: { kind: "unsent", billingMonth: "2026-10-01", total: 1, ready: 0, invoiceIds: ["a"] },
    },
    {
      name: "any invoice carries monthSentAt",
      invoices: [
        invoice({ id: "a", status: "SENT", monthSentAt: "2026-10-02T09:00:00Z" }),
        invoice({ id: "b", monthSentAt: "2026-10-02T09:00:00Z", sendableOnItsOwn: true }),
      ],
      expected: { kind: "sent", billingMonth: "2026-10-01", sentAt: "2026-10-02T09:00:00Z" },
    },
    {
      name: "a null invoice",
      invoices: [invoice({ id: "a" }), null],
      expected: { kind: "incomplete", billingMonth: "2026-10-01" },
    },
    {
      name: "a null invoice beside a sent one",
      invoices: [invoice({ id: "a", monthSentAt: "2026-10-02T09:00:00Z" }), null],
      expected: { kind: "sent", billingMonth: "2026-10-01", sentAt: "2026-10-02T09:00:00Z" },
    },
    {
      name: "every invoice null",
      invoices: [null, null],
      expected: { kind: "incomplete", billingMonth: null },
    },
  ])("$name", ({ invoices, expected }) => {
    expect(clientInvoiceMonth(invoices)).toEqual(expected);
  });

  it("is null with no Contracts", () => {
    expect(clientInvoiceMonth([])).toBeNull();
  });
});
