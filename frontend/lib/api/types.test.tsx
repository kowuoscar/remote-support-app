import { describe, expect, it } from "vitest";
import { missingCarrierInvoiceLines, type ClientInvoiceBaseSimLine } from "@/lib/api/types";

function line(simCardId: string): ClientInvoiceBaseSimLine {
  return { simCardId, number: `+1-${simCardId}`, monthlyFeeAmount: 10, cancellationEffectiveDate: null };
}

function file(simCardId: string | null) {
  return {
    id: `file-${simCardId}`,
    clientInvoiceId: "invoice-1",
    simCardId,
    filename: "x.pdf",
    contentType: "application/pdf",
    sizeBytes: 1,
    uploadedAt: "2026-10-01T00:00:00Z",
  };
}

describe("missingCarrierInvoiceLines", () => {
  it("returns the Postpaid SIM lines whose SIM has no file", () => {
    const missing = missingCarrierInvoiceLines({
      basePostpaidSims: [line("a"), line("b"), line("c")],
      files: [file("b")],
    });
    expect(missing.map((l) => l.simCardId)).toEqual(["a", "c"]);
  });

  it("returns nothing when every line has its file, and a file with no line counts for no line", () => {
    expect(
      missingCarrierInvoiceLines({ basePostpaidSims: [line("a")], files: [file("a"), file("gone"), file(null)] }),
    ).toEqual([]);
    expect(
      missingCarrierInvoiceLines({ basePostpaidSims: [line("a")], files: [file("gone"), file(null)] }),
    ).toHaveLength(1);
  });

  it("returns nothing for an invoice with no per-SIM breakdown", () => {
    expect(missingCarrierInvoiceLines({ files: [] })).toEqual([]);
    expect(missingCarrierInvoiceLines({ basePostpaidSims: [], files: [] })).toEqual([]);
  });
});
