import { describe, expect, it } from "vitest";
import { reviewMonthFor } from "./review-months";

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
