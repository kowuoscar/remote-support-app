import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  addTester,
  createClientAndContractWithSeededAgent,
  login,
  logout,
  selectContractInSwitcher,
} from "./helpers";

/**
 * The Client Invoice's send/approve lifecycle end to end (client-invoice-submission-and-visibility
 * ticket): an Agent sends their Contract's draft, a Tester at that Client sees it read-only and
 * downloads a PDF, and a Manager opens it from the Contract page's invoice summary and approves it on
 * the invoice's detail page (manager-invoice-review-queue spec) — driven against a real backend + Postgres (see
 * playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing decisions.
 * Mirrors tests/e2e/client-invoice-generation.spec.ts's pattern and helpers (this ticket's own
 * follow-on), which already covers the draft-building/attaching-a-file journey this one builds on
 * top of.
 */

const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("client invoice submission and visibility", () => {
  test("an agent sends a client invoice, a tester views and downloads it, a manager approves it", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Kessler & Vance ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    await addPostpaidSimCard(page, contractId, `+1-555-${RUN_ID.slice(-4)}`, "30.00");
    const testerEmail = `charlotte.finch+${RUN_ID}@kesslervance.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    // The Contract page summarises its current-month invoice; "Open invoice" works even for a
    // draft, landing on a read-only detail page.
    await page.goto(`/manager/contracts/${contractId}`);
    const draftSummary = page.getByRole("region", { name: "Client Invoice" });
    await expect(draftSummary.getByText("Draft")).toBeVisible();
    await draftSummary.getByRole("link", { name: "Open invoice" }).click();
    await expect(page).toHaveURL(/\/manager\/invoices\/client\/.+/);
    await expect(page.getByRole("heading", { name: `${clientName} — Jordan Ellis` })).toBeVisible();
    await expect(page.getByText("Draft", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toHaveCount(0);

    // Agent opens the draft (creating it as a side effect), then sends it.
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/client-invoices");
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByText("Draft")).toBeVisible();

    await page.getByRole("button", { name: "Send Client Invoice" }).click();
    const [sendResponse] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes("/client-invoice/send") && resp.request().method() === "POST",
      ),
      page.getByRole("button", { name: "Confirm send" }).click(),
    ]);
    expect(sendResponse.status()).toBe(200);

    // Sent — no longer editable: the Attach/Send controls are gone, a PDF download appears
    // instead (ticket AC: "a sent invoice is no longer editable by the Agent").
    await expect(page.getByText("Awaiting approval")).toBeVisible();
    await expect(page.getByRole("button", { name: "Attach carrier invoice" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Send Client Invoice" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Download PDF" })).toBeVisible();

    await logout(page);

    // Tester sees the now-sent invoice read-only and downloads a PDF of it.
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/invoices");
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByText("Awaiting approval")).toBeVisible();
    // "$30.00" appears twice here (base amount and total, since no Fees were logged) — both
    // correct, so this only needs to confirm at least one renders (mirrors
    // client-invoice-generation.spec.ts's note on the same ambiguity).
    await expect(page.getByText("$30.00", { exact: true }).first()).toBeVisible();

    const [download] = await Promise.all([
      page.waitForEvent("download"),
      page.getByRole("link", { name: "Download PDF" }).click(),
    ]);
    expect(download.suggestedFilename()).toMatch(/^client-invoice-.*\.pdf$/);

    await logout(page);

    // Manager opens it from the Contract page's summary — which offers no approve action of its
    // own — and approves it on the invoice's detail page.
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto(`/manager/contracts/${contractId}`);
    const summary = page.getByRole("region", { name: "Client Invoice" });
    await expect(summary.getByText("Awaiting approval")).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toHaveCount(0);
    await summary.getByRole("link", { name: "Open invoice" }).click();
    await expect(page).toHaveURL(/\/manager\/invoices\/client\/.+/);
    const detailUrl = page.url();

    const [approveResponse] = await Promise.all([
      page.waitForResponse(
        (resp) =>
          resp.url().includes("/api/client-invoices/") &&
          resp.url().endsWith("/approve") &&
          resp.request().method() === "POST",
      ),
      page.getByRole("button", { name: "Approve" }).click(),
    ]);
    expect(approveResponse.status()).toBe(200);

    await expect(page.getByText("Approved", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toHaveCount(0);

    // The Contract page's summary reflects the approval and still links to the same invoice.
    await page.goto(`/manager/contracts/${contractId}`);
    await expect(summary.getByText("Approved", { exact: true })).toBeVisible();
    await summary.getByRole("link", { name: "Open invoice" }).click();
    await expect(page).toHaveURL(detailUrl);
  });
});
