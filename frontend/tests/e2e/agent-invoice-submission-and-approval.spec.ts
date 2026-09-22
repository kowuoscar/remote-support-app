import { test, expect, type Page } from "@playwright/test";

/**
 * The Agent Invoice's send/override/approve/paid lifecycle end to end
 * (agent-invoice-submission-and-approval ticket): the Agent sends their own draft, a Manager
 * opens it from the Agent page's invoice summary and, on the invoice's detail page
 * (manager-invoice-review-queue spec), overrides the Salary line, approves it, then marks it
 * paid — driven against a real backend + Postgres (see playwright.e2e.config.ts), at the
 * accessibility-tree level per spec.md's Testing decisions. Mirrors
 * client-invoice-submission-and-visibility.spec.ts's pattern/helpers.
 *
 * Only the seeded Agent ("Jordan Ellis" / agent@example.com) has a login, so this test operates
 * on that Agent's own current-month Agent Invoice — a resource keyed by (Agent, calendar month),
 * not a fresh-per-run resource like a Client Invoice's Contract. Unlike
 * client-invoice-submission-and-visibility.spec.ts (which creates a brand new Client/Contract
 * every run so it can send a fresh Client Invoice each time), this test therefore is not safely
 * re-runnable within the same calendar month against the same Postgres data — the invoice this
 * test sends/approves/pays stays in that state until the month rolls over or the database is
 * reset (`docker compose down -v && docker compose up -d postgres`, the same reset any change to
 * seeded data already requires).
 */
const SEEDED_USERS = {
  manager: { username: "manager@example.com", password: "ChangeMe123!" },
  agent: { username: "agent@example.com", password: "AgentDemo123!" },
} as const;

async function login(page: Page, username: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

async function logout(page: Page) {
  // viewer-chip-menu ticket: "Log out" moved into the viewer chip's menu.
  await page.locator('[aria-haspopup="menu"]').click();
  await page.getByRole("menuitem", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login/);
}

test.describe("agent invoice submission and approval", () => {
  test("an agent sends their invoice, a manager overrides a value, approves it, and marks it paid", async ({
    page,
  }) => {
    // Agent sends their own draft Agent Invoice.
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/my-invoice");
    await expect(page.getByText("Draft")).toBeVisible();

    await page.getByRole("button", { name: "Send Agent Invoice" }).click();
    const [sendResponse] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes("/invoice/send") && resp.request().method() === "POST",
      ),
      page.getByRole("button", { name: "Confirm send" }).click(),
    ]);
    expect(sendResponse.status()).toBe(200);

    // Sent — no longer editable by the Agent (ticket AC): the Send control is gone.
    await expect(page.getByText("Awaiting approval")).toBeVisible();
    await expect(page.getByRole("button", { name: "Send Agent Invoice" })).toHaveCount(0);

    await logout(page);

    // Manager opens it from the Agent page's summary — which offers no review action of its own —
    // then overrides the Salary line and approves it on the invoice's detail page.
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/agents");
    await page.getByRole("link", { name: "Jordan Ellis" }).click();
    await expect(page).toHaveURL(/\/manager\/agents\/.+/);
    const agentPageUrl = page.url();
    const summary = page.getByRole("region", { name: "Agent Invoice" });
    await expect(summary.getByText("Awaiting approval")).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toHaveCount(0);
    await expect(page.getByLabel(/Override Salary/)).toHaveCount(0);
    // Standing amounts stay on the Agent page.
    await expect(page.getByText("Standing amounts")).toBeVisible();
    await summary.getByRole("link", { name: "Open invoice" }).click();
    await expect(page).toHaveURL(/\/manager\/invoices\/agent\/.+/);
    await expect(page.getByRole("heading", { name: "Jordan Ellis" })).toBeVisible();
    const detailUrl = page.url();

    const salaryLine = page.locator("dt", { hasText: /^Salary$/ }).locator("xpath=following-sibling::dd[1]");
    const originalSalaryText = await salaryLine.textContent();

    const salaryOverrideInput = page.getByLabel(/Override Salary/);
    const overrideForm = page.locator("form").filter({ has: salaryOverrideInput });
    await salaryOverrideInput.fill("3333.00");
    const [overrideResponse] = await Promise.all([
      page.waitForResponse(
        (resp) =>
          resp.url().includes("/api/agent-invoices/") &&
          resp.url().endsWith("/override") &&
          resp.request().method() === "POST",
      ),
      overrideForm.getByRole("button", { name: "Override" }).click(),
    ]);
    expect(overrideResponse.status()).toBe(200);
    await expect(overrideForm.getByText("Applied to this invoice only.")).toBeVisible();

    // The override lands on the invoice's own Salary line — a different figure than before (ticket
    // AC: "without changing the Agent's standing amount used by future invoices").
    await expect(salaryLine).not.toHaveText(originalSalaryText ?? "");
    await expect(salaryLine).toHaveText(/3,333\.00/);

    const [approveResponse] = await Promise.all([
      page.waitForResponse(
        (resp) =>
          resp.url().includes("/api/agent-invoices/") &&
          resp.url().endsWith("/approve") &&
          resp.request().method() === "POST",
      ),
      page.getByRole("button", { name: "Approve" }).click(),
    ]);
    expect(approveResponse.status()).toBe(200);

    await expect(page.getByText("Approved", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toHaveCount(0);

    // Manager marks it paid — purely a status flag, no payment executed by the app.
    const [paidResponse] = await Promise.all([
      page.waitForResponse(
        (resp) =>
          resp.url().includes("/api/agent-invoices/") &&
          resp.url().endsWith("/paid") &&
          resp.request().method() === "POST",
      ),
      page.getByRole("button", { name: "Mark paid" }).click(),
    ]);
    expect(paidResponse.status()).toBe(200);

    await expect(page.getByText("Paid", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Mark paid" })).toHaveCount(0);

    // The Agent page's summary reflects the final status and still links to the same invoice.
    await page.goto(agentPageUrl);
    await expect(summary.getByText("Paid", { exact: true })).toBeVisible();
    await summary.getByRole("link", { name: "Open invoice" }).click();
    await expect(page).toHaveURL(detailUrl);

    await logout(page);

    // The Agent sees the final status on their own invoice too (ticket AC: "Agent can see the
    // status history of their own invoices"), with the Manager's override reflected in the total.
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/my-invoice");
    await expect(page.getByText("Paid", { exact: true })).toBeVisible();
  });
});
