import { test, expect, type Locator } from "@playwright/test";
import { gotoWithSession } from "./helpers";

/**
 * side-list-row-one-line: the side list row's geometry, by bounding box, against the stub backend
 * (the fixtures hold a name collision, a status-plus-mark row and, in a session of its own, a
 * mark-only row). Not goldens.
 */
async function box(locator: Locator) {
  const result = await locator.boundingBox();
  if (!result) throw new Error("element has no box");
  return result;
}
const overlapsVertically = (a: { y: number; height: number }, b: { y: number; height: number }) =>
  a.y < b.y + b.height && b.y < a.y + a.height;

test.describe("the Agent's Clients column rows", () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
  });

  test("a collision puts the Country under the name, the status stays on line 1", async ({ page }) => {
    await gotoWithSession(page, "/agent/client-invoices", "visual-agent-invoices-session");
    const clients = page.getByRole("navigation", { name: "Clients" });
    const row = clients.getByRole("link", { name: "Birch Telecom Mexico Approved", exact: true });
    const name = row.locator("span[title]");
    const country = row.getByText("Mexico", { exact: true });
    const badge = row.getByText("Approved", { exact: true });
    const [nameBox, countryBox, badgeBox, rowBox] = await Promise.all([box(name), box(country), box(badge), box(row)]);
    expect(countryBox.y).toBeGreaterThanOrEqual(nameBox.y + nameBox.height);
    expect(overlapsVertically(nameBox, badgeBox)).toBe(true);
    expect(badgeBox.x).toBeGreaterThan(nameBox.x + nameBox.width - 1);
    expect(badgeBox.x + badgeBox.width).toBeLessThanOrEqual(rowBox.x + rowBox.width - 8);
    // No second line on a row that has neither a collision nor the mark.
    const plain = clients.getByRole("link", { name: "Aurora Retail Group Draft", exact: true });
    const plainBox = await box(plain);
    const plainBadge = await box(plain.getByText("Draft", { exact: true }));
    expect(plainBox.height).toBeLessThanOrEqual(plainBadge.height + 16 + 1);
  });

  test("a status plus the mark: the mark once, under the status, right-aligned with it", async ({ page }) => {
    await gotoWithSession(page, "/agent/client-invoices", "visual-agent-invoices-session");
    const clients = page.getByRole("navigation", { name: "Clients" });
    const row = clients.getByRole("link", { name: "Birch Telecom United Kingdom Awaiting approval Sent back", exact: true });
    await expect(row.getByText("Sent back")).toHaveCount(1);
    const status = row.getByText("Awaiting approval", { exact: true });
    const mark = row.getByText("Sent back");
    const [statusBox, markBox] = await Promise.all([box(status), box(mark)]);
    expect(markBox.y).toBeGreaterThanOrEqual(statusBox.y + statusBox.height);
    // Both are the Badge itself, so their boxes are the pills'.
    const statusBadge = statusBox;
    const markBadge = markBox;
    expect(Math.abs(statusBadge.x + statusBadge.width - (markBadge.x + markBadge.width))).toBeLessThanOrEqual(1);
  });

  test("a mark with no status sits on line 1 in the status's place", async ({ page }) => {
    await gotoWithSession(page, "/agent/client-invoices", "visual-agent-invoice-failed-marked-session");
    const clients = page.getByRole("navigation", { name: "Clients" });
    const row = clients.getByRole("link", { name: "Birch Telecom United Kingdom Sent back", exact: true });
    const [nameBox, markBox, rowBox] = await Promise.all([
      box(row.locator("span[title]")),
      box(row.getByText("Sent back")),
      box(row),
    ]);
    expect(overlapsVertically(nameBox, markBox)).toBe(true);
    expect(markBox.x + markBox.width).toBeLessThanOrEqual(rowBox.x + rowBox.width - 8);
  });
});
