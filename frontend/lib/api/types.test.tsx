import { describe, expect, it } from "vitest";
import { missingCarrierInvoiceLines, type ClientInvoiceBaseSimLine } from "@/lib/api/types";

function line(simCardId: string, carrierInvoiceFileId?: string | null): ClientInvoiceBaseSimLine {
  return {
    simCardId,
    number: `+1-${simCardId}`,
    monthlyFeeAmount: 10,
    cancellationEffectiveDate: null,
    carrierInvoiceFileId,
  };
}

describe("missingCarrierInvoiceLines", () => {
  it("returns the Postpaid SIM lines whose carrierInvoiceFileId is null or absent", () => {
    const missing = missingCarrierInvoiceLines({
      basePostpaidSims: [line("a", null), line("b", "file-b"), line("c")],
    });
    expect(missing.map((l) => l.simCardId)).toEqual(["a", "c"]);
  });

  it("returns nothing when every line has its file", () => {
    expect(missingCarrierInvoiceLines({ basePostpaidSims: [line("a", "file-a")] })).toEqual([]);
  });

  it("returns nothing for an invoice with no per-SIM breakdown", () => {
    expect(missingCarrierInvoiceLines({})).toEqual([]);
    expect(missingCarrierInvoiceLines({ basePostpaidSims: [] })).toEqual([]);
  });
});
