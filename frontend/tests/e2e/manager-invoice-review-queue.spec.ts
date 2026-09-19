import { execFileSync } from "node:child_process";
import path from "node:path";
import { test, expect, type Page } from "@playwright/test";
import { Client } from "pg";
import {
  SEEDED_USERS,
  createClientAndContractWithSeededAgent,
  login,
  logout,
  selectContractInSwitcher,
} from "./helpers";

/**
 * The Manager's Review Queue end to end (manager-invoice-review-queue spec; client-invoice-review-page
 * and agent-invoice-review-page tickets): an Agent sends an invoice, the Manager opens it from the
 * Review Queue, acts on it on its detail page, sees the final state, and finds it gone from the
 * queue. Business rules (ordering, past months, 403/404) are covered at the API seam.
 */

/** The seeded agent@example.com login's display name — used in headings/labels this suite reads. */
const SEEDED_AGENT_NAME = "Jordan Ellis";

/** Sends the current month's Client Invoice for `clientName`'s Contract as the seeded Agent. */
async function sendClientInvoiceAsAgent(page: Page, clientName: string) {
  await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
  await page.goto("/agent/client-invoices");
  await selectContractInSwitcher(page, clientName);
  await page.getByRole("button", { name: "Send Client Invoice" }).click();
  await page.getByRole("button", { name: "Confirm send" }).click();
  await expect(page.getByText("Awaiting approval")).toBeVisible();
  await logout(page);
}

/**
 * Approves every waiting Client Invoice except `keepSubject`'s, so that one is among the few the
 * Dashboard card shows however many earlier runs left waiting in this database.
 */
async function approveOtherWaitingClientInvoices(page: Page, keepSubject: string) {
  for (;;) {
    await page.goto("/manager/invoices");
    await expect(page.getByRole("tablist", { name: "Filter the Review Queue by invoice type" })).toBeVisible();
    let target = null;
    for (const link of await page.getByRole("link", { name: /^Review Client Invoice: / }).all()) {
      const name = await link.getAttribute("aria-label");
      if (!name?.startsWith(`Review Client Invoice: ${keepSubject},`)) {
        target = link;
        break;
      }
    }
    if (!target) return;
    await target.click();
    await expect(page).toHaveURL(/\/manager\/invoices\/client\/.+/);
    await page.getByRole("button", { name: "Approve" }).click();
    await expect(page.getByText("Approved", { exact: true })).toBeVisible();
  }
}

const SEEDED_AGENT_ID = "55555555-5555-5555-5555-555555555555";

/**
 * Stands in for a month rollover on the seeded Agent's Agent Invoice, the same way the API tests
 * move an invoice into a past billing month: Jordan Ellis is the only Agent with a login, so his
 * one current-month invoice is shared with agent-invoice-submission-and-approval.spec.ts, which
 * leaves it paid. Moving whatever current-month invoice he has to a month before all his others
 * frees the current month, so this test always starts from a draft it can send. Runs against the
 * e2e Postgres that scripts/run-backend-for-e2e.sh starts (honours COMPOSE_PROJECT_NAME) — or,
 * when E2E_DATABASE_URL is set, against that database directly, so a run on an isolated stack
 * never reaches for docker-compose's own Postgres.
 */
async function rollSeededAgentInvoiceIntoThePast() {
  const sql = `
    update agent_invoices
    set billing_month = (
      select (min(billing_month) - interval '1 month')::date from agent_invoices where agent_id = '${SEEDED_AGENT_ID}'
    )
    where agent_id = '${SEEDED_AGENT_ID}'
      and billing_month = date_trunc('month', now() at time zone 'utc')::date`;
  if (process.env.E2E_DATABASE_URL) {
    const client = new Client({ connectionString: process.env.E2E_DATABASE_URL });
    await client.connect();
    try {
      await client.query(sql);
    } finally {
      await client.end();
    }
    return;
  }
  execFileSync(
    "docker",
    [
      "compose",
      "-f",
      path.resolve(__dirname, "../../../docker-compose.yml"),
      "exec",
      "-T",
      "postgres",
      "psql",
      "-v",
      "ON_ERROR_STOP=1",
      "-U",
      "remote_support",
      "-d",
      "remote_support",
      "-c",
      sql,
    ],
    { stdio: "pipe" },
  );
}

function currentBillingMonthLabel(): string {
  return new Date().toLocaleDateString("en-US", { month: "long", year: "numeric", timeZone: "UTC" });
}

const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("manager invoice review queue", () => {
  test("a manager opens a sent client invoice from the review queue, approves it, and it leaves the queue", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Harbor & Finch ${RUN_ID}`;
    await createClientAndContractWithSeededAgent(page, clientName);
    await logout(page);

    // The Agent sends this Contract's Client Invoice.
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/client-invoices");
    await selectContractInSwitcher(page, clientName);
    await page.getByRole("button", { name: "Send Client Invoice" }).click();
    await page.getByRole("button", { name: "Confirm send" }).click();
    await expect(page.getByText("Awaiting approval")).toBeVisible();
    await logout(page);

    // The Manager finds it in the Review Queue and opens its detail page.
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/invoices");
    const subject = `${clientName} — ${SEEDED_AGENT_NAME}`;
    const row = page.getByRole("row", { name: new RegExp(subject) });
    await expect(row).toBeVisible();
    await expect(row.getByText("Client Invoice")).toBeVisible();
    await row.getByRole("link", { name: new RegExp(`^Review Client Invoice: ${subject}`) }).click();

    await expect(page).toHaveURL(/\/manager\/invoices\/client\/.+/);
    await expect(page.getByRole("heading", { name: subject })).toBeVisible();
    await expect(page.getByText("Awaiting approval")).toBeVisible();
    await expect(page.getByRole("link", { name: "Download PDF" })).toBeVisible();

    // Approve: the page shows the final, read-only state in place.
    const [approveResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/approve") && resp.request().method() === "POST"),
      page.getByRole("button", { name: "Approve" }).click(),
    ]);
    expect(approveResponse.status()).toBe(200);
    await expect(page.getByText("Approved", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toHaveCount(0);

    // Back on the queue, it's gone.
    await page.getByRole("navigation", { name: "Breadcrumb" }).getByRole("link", { name: "Invoices" }).click();
    await expect(page).toHaveURL(/\/manager\/invoices$/);
    await expect(page.getByRole("row", { name: new RegExp(subject) })).toHaveCount(0);
  });

  test("a manager opens a sent invoice from the dashboard's pending approvals card", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Lantern Row ${RUN_ID}`;
    await createClientAndContractWithSeededAgent(page, clientName);
    await logout(page);

    await sendClientInvoiceAsAgent(page, clientName);

    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const subject = `${clientName} — ${SEEDED_AGENT_NAME}`;
    await approveOtherWaitingClientInvoices(page, subject);

    await page.goto("/manager");
    const card = page.getByRole("list", { name: "Pending approvals" });
    await card.getByRole("link", { name: new RegExp(subject) }).click();

    await expect(page).toHaveURL(/\/manager\/invoices\/client\/.+/);
    await expect(page.getByRole("heading", { name: subject })).toBeVisible();
  });

  test("a manager overrides and approves a sent agent invoice from the review queue, then marks it paid and it leaves the queue", async ({
    page,
  }) => {
    await rollSeededAgentInvoiceIntoThePast();

    // The Agent sends their Agent Invoice.
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/my-invoice");
    await page.getByRole("button", { name: "Send Agent Invoice" }).click();
    await page.getByRole("button", { name: "Confirm send" }).click();
    await expect(page.getByText("Awaiting approval")).toBeVisible();
    await logout(page);

    // The Manager finds it in the Review Queue and opens its detail page.
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/invoices");
    const reviewLinkName = `Review Agent Invoice: ${SEEDED_AGENT_NAME}, ${currentBillingMonthLabel()}`;
    const row = page.getByRole("row").filter({ has: page.getByRole("link", { name: reviewLinkName }) });
    await expect(row).toBeVisible();
    await expect(row.getByText("Approval")).toBeVisible();
    await row.getByRole("link", { name: reviewLinkName }).click();

    await expect(page).toHaveURL(/\/manager\/invoices\/agent\/.+/);
    await expect(page.getByRole("heading", { name: SEEDED_AGENT_NAME })).toBeVisible();
    await expect(page.getByText("Awaiting approval")).toBeVisible();

    // Override Salary: the line and total update in place.
    const salaryLine = page.locator("dt", { hasText: /^Salary$/ }).locator("xpath=following-sibling::dd[1]");
    const salaryInput = page.getByLabel(/Override Salary/);
    const overrideForm = page.locator("form").filter({ has: salaryInput });
    await salaryInput.fill("4321.00");
    await overrideForm.getByRole("button", { name: "Override" }).click();
    await expect(overrideForm.getByText("Applied to this invoice only.")).toBeVisible();
    await expect(salaryLine).toHaveText(/4,321\.00/);

    // Approve: now awaiting payment, and still in the Review Queue.
    await page.getByRole("button", { name: "Approve" }).click();
    await expect(page.getByText("Approved", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Mark paid" })).toBeVisible();

    await page.getByRole("navigation", { name: "Breadcrumb" }).getByRole("link", { name: "Invoices" }).click();
    await expect(page).toHaveURL(/\/manager\/invoices$/);
    await expect(row).toBeVisible();
    await expect(row.getByText("Payment")).toBeVisible();
    await row.getByRole("link", { name: reviewLinkName }).click();

    // Mark paid: the final, read-only state.
    await page.getByRole("button", { name: "Mark paid" }).click();
    await expect(page.getByText("Paid", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Mark paid" })).toHaveCount(0);
    await expect(salaryLine).toHaveText(/4,321\.00/);

    // Back on the queue, it's gone.
    await page.getByRole("navigation", { name: "Breadcrumb" }).getByRole("link", { name: "Invoices" }).click();
    await expect(page).toHaveURL(/\/manager\/invoices$/);
    await expect(page.getByRole("link", { name: reviewLinkName })).toHaveCount(0);
  });
});
