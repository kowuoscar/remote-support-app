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
    // The readiness is a second line of its own: the badge stays beside the name on line 1.
    const plain = clients.getByRole("link", { name: "Aurora Retail Group Draft 2 files missing", exact: true });
    const plainBox = await box(plain);
    const plainName = await box(plain.locator("span[title]"));
    const plainBadge = await box(plain.getByText("Draft", { exact: true }));
    const plainReadiness = await box(plain.getByText("2 files missing", { exact: true }));
    expect(overlapsVertically(plainName, plainBadge)).toBe(true);
    expect(plainReadiness.y).toBeGreaterThanOrEqual(plainName.y + plainName.height);
    expect(plainBox.height).toBeLessThanOrEqual(plainBadge.height + 4 + plainReadiness.height + 16 + 1);
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

  test("a readiness mark sits after the Country on line 2, with the Sent back mark under the status", async ({ page }) => {
    await gotoWithSession(page, "/agent/client-invoices", "visual-agent-invoices-unsent-marked-session");
    const clients = page.getByRole("navigation", { name: "Clients" });
    const row = clients.getByRole("link", { name: "Birch Telecom United Kingdom Draft Sent back Ready", exact: true });
    const [nameBox, countryBox, readyBox, statusBox, markBox, rowBox] = await Promise.all([
      box(row.locator("span[title]")),
      box(row.getByText("United Kingdom", { exact: true })),
      box(row.getByText("Ready", { exact: true })),
      box(row.getByText("Draft", { exact: true })),
      box(row.getByText("Sent back")),
      box(row),
    ]);
    // Line 1: the name and the status. Line 2: the Country, then the readiness, then the mark on the right.
    expect(overlapsVertically(nameBox, statusBox)).toBe(true);
    expect(overlapsVertically(countryBox, readyBox)).toBe(true);
    expect(overlapsVertically(readyBox, markBox)).toBe(true);
    expect(countryBox.y).toBeGreaterThanOrEqual(nameBox.y + nameBox.height);
    expect(readyBox.x).toBeGreaterThanOrEqual(countryBox.x + countryBox.width);
    expect(readyBox.x + readyBox.width).toBeLessThanOrEqual(markBox.x);
    expect(markBox.x + markBox.width).toBeLessThanOrEqual(rowBox.x + rowBox.width - 8);
    // The row is two lines high, whatever sits on line 2.
    expect(rowBox.height).toBeLessThanOrEqual(statusBox.height + 4 + markBox.height + 16 + 1);
  });
});
