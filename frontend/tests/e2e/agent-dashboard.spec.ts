import { test, expect, type Page } from "@playwright/test";
import { SEEDED_USERS, login, logout, rollSeededAgentInvoiceIntoThePast } from "./helpers";

/**
 * The Agent dashboard's invoice figures end to end (real-agent-dashboard spec; ticket
 * agent-dashboard-invoice-figures), against a real backend + Postgres at the accessibility-tree
 * level. The Local Support Fees and My Invoice status cards read the same get-or-create invoice
 * My Invoice reads, so each case compares the dashboard with that page or with the Review Queue.
 * `agent-dashboard-requests` extends this file with the Request cases.
 */

interface InvoiceFigures {
  month: string;
  status: string;
  localSupportFees: string;
}

/** What /agent/my-invoice shows for the current invoice: its month, status label and fees line. */
async function readMyInvoice(page: Page): Promise<InvoiceFigures> {
  await page.goto("/agent/my-invoice");
  const heading = page.getByRole("heading", { level: 2 }).first();
  const badge = heading.locator("xpath=following-sibling::*[1]");
  const fees = page.locator("dt", { hasText: /^Local Support Fees$/ }).locator("xpath=following-sibling::dd[1]");
  return {
    month: (await heading.textContent())!.trim(),
    status: (await badge.textContent())!.trim(),
    localSupportFees: (await fees.textContent())!.trim(),
  };
}

/** What the dashboard's two invoice cards show. */
async function readDashboard(page: Page): Promise<InvoiceFigures & { feesMeta: string }> {
  await page.goto("/agent");
  const feesCard = page.getByTestId("local-support-fees-stat");
  const statusCard = page.getByTestId("invoice-status-stat");
  await expect(feesCard).toBeVisible();
  const label = (await feesCard.locator(":scope > span").nth(0).textContent())!;
  const month = label.replace(/^Local Support Fees — /, "");
  await expect(statusCard).toContainText(month);
  return {
    month,
    status: (await statusCard.locator(":scope > span").nth(1).textContent())!.trim(),
    localSupportFees: (await feesCard.locator(":scope > span").nth(1).textContent())!.trim(),
    feesMeta: (await feesCard.locator(":scope > span").nth(2).textContent())!.trim(),
  };
}

async function reviewQueueLinks(page: Page): Promise<string[]> {
  await page.goto("/manager/invoices");
  await expect(page.getByRole("tablist", { name: "Filter the Review Queue by invoice type" })).toBeVisible();
  const links = await page.getByRole("link", { name: /^Review (Client|Agent) Invoice: / }).all();
  return ((await Promise.all(links.map((link) => link.getAttribute("aria-label")))) as string[]).sort();
}

test.describe("agent dashboard invoice figures", () => {
  test("dashboard-shows-invoice-status-and-amounts", async ({ page }) => {
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    const invoice = await readMyInvoice(page);
    const dashboard = await readDashboard(page);

    expect(dashboard.month).toBe(invoice.month);
    expect(dashboard.localSupportFees).toBe(invoice.localSupportFees);
    expect(dashboard.status).toBe(invoice.status);
    expect(dashboard.feesMeta).toBe(
      invoice.status === "Draft" ? "Running total, all your Contracts" : "As sent on your invoice",
    );
  });

  test("dashboard-draft-is-the-one-my-invoice-opens", async ({ page }) => {
    await rollSeededAgentInvoiceIntoThePast();
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);

    // First visit of the month is the dashboard: it creates the Draft.
    const dashboard = await readDashboard(page);
    expect(dashboard.status).toBe("Draft");
    expect(dashboard.feesMeta).toBe("Running total, all your Contracts");

    // My Invoice opens that same Draft, still editable (Send control present).
    const invoice = await readMyInvoice(page);
    expect(invoice).toEqual({
      month: dashboard.month,
      status: "Draft",
      localSupportFees: dashboard.localSupportFees,
    });
    await expect(page.getByRole("button", { name: "Send Agent Invoice" })).toBeVisible();
  });

  test("dashboard-visit-leaves-review-queue-unchanged", async ({ page }) => {
    await rollSeededAgentInvoiceIntoThePast();

    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const before = await reviewQueueLinks(page);
    await logout(page);

    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    const dashboard = await readDashboard(page);
    expect(dashboard.status).toBe("Draft");
    await logout(page);

    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    expect(await reviewQueueLinks(page)).toEqual(before);
  });
});
