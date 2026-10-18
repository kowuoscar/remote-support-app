import { describe, expect, it } from "vitest";
import type { ReviewClientInvoice, ReviewStatusValue } from "@/lib/api/types";
import { nextToReview, reviewMonthFor } from "./review-months";

const CURRENT = "2026-11-01";

// Newest first, as the server serves them.
const MONTHS = [
  { billingMonth: "2026-10-01", waiting: 0 },
  { billingMonth: "2026-09-01", waiting: 2 },
  { billingMonth: "2026-08-01", waiting: 1 },
  { billingMonth: "2026-07-01", waiting: 0 },
];

describe("reviewMonthFor", () => {
  it.each([
    ["a valid param, listed", MONTHS, "2026-07", "2026-07-01"],
    ["a valid param the Agent never sent", MONTHS, "2025-01", "2025-01-01"],
    ["no param: the oldest month with something waiting", MONTHS, undefined, "2026-08-01"],
    ["no param, nothing waiting: the newest listed month", MONTHS.map((m) => ({ ...m, waiting: 0 })), undefined, "2026-10-01"],
    ["no param, no months: the current month", [], undefined, CURRENT],
    ["a malformed param (month 13): the oldest waiting", MONTHS, "2026-13", "2026-08-01"],
    ["a param of the wrong shape", MONTHS, "october", "2026-08-01"],
    ["a full date is not a month param", MONTHS, "2026-07-01", "2026-08-01"],
    ["a malformed param with no months: the current month", [], "nope", CURRENT],
  ])("%s", (_name, months, param, expected) => {
    expect(reviewMonthFor(months, param, CURRENT)).toBe(expected);
  });
});

function invoiceRow(id: string, reviewStatus: ReviewStatusValue): ReviewClientInvoice {
  return {
    id,
    contractId: `contract-${id}`,
    clientName: `Client ${id}`,
    reviewStatus,
    currency: "USD",
    totalAmount: 10,
    sentAt: "2026-10-12T12:00:00Z",
    approvedAt: null,
    sentBackAt: null,
  };
}

describe("nextToReview", () => {
  const A = invoiceRow("a", "TO_REVIEW");
  const B = invoiceRow("b", "SENT_BACK");
  const C = invoiceRow("c", "TO_REVIEW");
  const D = invoiceRow("d", "APPROVED");
  const E = invoiceRow("e", "TO_REVIEW");

  it.each([
    ["the first To review row after the current one, skipping other statuses", [A, B, C, D, E], "a", "c"],
    ["wrapping round to the start after the last", [A, B, C, D, E], "e", "a"],
    ["wrapping round when the current is last in the list and only others are left", [A, B, C], "c", "a"],
    ["the current row's successor even when the current is not To review", [A, B, C, D, E], "b", "c"],
    ["the first To review row when the current is not listed", [A, B, C], "zzz", "a"],
    ["the first To review row for an empty current id", [B, C], "", "c"],
  ])("returns %s", (_name, rows, currentId, expectedId) => {
    expect(nextToReview(rows, currentId)?.id).toBe(expectedId);
  });

  it.each([
    ["only the current row is To review", [A, B, D], "a"],
    ["nothing is To review", [B, D], "b"],
    ["the month is empty", [], "a"],
  ])("returns null when %s", (_name, rows, currentId) => {
    expect(nextToReview(rows, currentId)).toBeNull();
  });
});
