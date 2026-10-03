import { test, expect, type Locator, type Page } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  addTesterAndSubmitRequestAsAgent,
  createClientAndContractWithSeededAgent,
  login,
  logout,
  rollSeededAgentInvoiceIntoThePast,
  selectContractInSwitcher,
} from "./helpers";

/**
 * An Agent edits the amounts a draft Client Invoice bills, and resets one (edit-client-invoice-
 * lines ticket `agent-edits-lines-on-the-client-invoice-page`, user stories 2-6, 11, 14), driven
 * against a real backend + Postgres at the accessibility-tree level. Prior art:
 * client-invoice-generation.spec.ts ($25.00 base, $45.00 Fee, $70.00 total). The journey closes
 * with the Manager reading the sent invoice (ticket `manager-sees-edited-client-invoice-lines`,
 * user stories 16-18, 24).
 */
const SEEDED_AGENT_NAME = "Jordan Ellis";
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

async function readLocalSupportFees(page: Page): Promise<number> {
  await page.goto("/agent/my-invoice");
  const fees = page.locator("dt", { hasText: /^Local Support Fees$/ }).locator("xpath=following-sibling::dd[1]");
  await expect(fees).toBeVisible();
  return Number((await fees.textContent())!.replace(/[^0-9.-]/g, ""));
}

/**
 * The Dashboard's Pending approvals card shows only the oldest few waiting invoices, so approve
 * every other waiting Client Invoice first (same approach as manager-invoice-review-queue.spec.ts).
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

async function editLine(page: Page, row: Locator, amount: string) {
  await row.getByRole("button", { name: "Edit" }).click();
  const field = page.getByRole("textbox", { name: /Billed amount for/ });
  await field.fill(amount);
  await page.getByRole("button", { name: "Save" }).click();
  await expect(field).toBeHidden();
}

test.describe("edit client invoice lines", () => {
  test("agent-edits-lines-sees-totals-and-agent-invoice-follow-then-resets-and-sends", async ({ page }) => {
    // The seeded Agent's one current-month Agent Invoice is shared by every spec; start from a
    // fresh draft so it follows this test's edits.
    await rollSeededAgentInvoiceIntoThePast();

    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Adjusted Lines Client ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const simNumber = `+1-555-${RUN_ID.slice(-4)}`;
    await addPostpaidSimCard(page, contractId, simNumber, "25.00");
    await addTesterAndSubmitRequestAsAgent(
      page,
      clientId,
      clientName,
      `edit.lines+${RUN_ID}@aurora.example`,
      "Topup",
      { simCardOptionLabel: `${simNumber} — Verizon` },
    );
    const requestRow = page.getByRole("row", { name: /Topup/ });
    await requestRow.getByRole("button", { name: "Mark In Progress" }).click();
    await requestRow.getByRole("button", { name: "Mark Completed" }).click();
    await requestRow.getByLabel(/Fee amount/).fill("45.00");
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      requestRow.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);

    const feesBefore = await readLocalSupportFees(page);

    await page.goto("/agent/client-invoices");
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByText("You can adjust any line to what was actually billed.")).toBeVisible();
    await expect(page.getByText("$70.00", { exact: true })).toBeVisible();

    const simRow = page.getByRole("row", { name: new RegExp(simNumber.replace("+", "\\+")) });
    const feeRow = page.getByRole("row", { name: /Topup/ });

    await editLine(page, simRow, "31.40");
    await expect(simRow.getByText("$31.40", { exact: true })).toBeVisible();
    await expect(simRow.getByText("Edited · computed $25.00")).toBeVisible();

    await editLine(page, feeRow, "40.00");
    await expect(feeRow.getByText("Edited · computed $45.00")).toBeVisible();
    await expect(page.getByText("$71.40", { exact: true })).toBeVisible();

    // The Agent Invoice's Local Support Fees follow: +$6.40 on the SIM, -$5.00 on the Fee.
    expect(await readLocalSupportFees(page)).toBeCloseTo(feesBefore + 1.4, 2);

    await page.goto("/agent/client-invoices");
    await selectContractInSwitcher(page, clientName);
    await feeRow.getByRole("button", { name: "Reset" }).click();
    await expect(feeRow.getByText(/Edited · computed/)).toBeHidden();
    await expect(page.getByText("$76.40", { exact: true })).toBeVisible();

    await page.getByRole("button", { name: "Send Client Invoice" }).click();
    await page.getByRole("button", { name: "Confirm send" }).click();
    await expect(page.getByText("Awaiting approval")).toBeVisible();
    await expect(page.getByRole("button", { name: /^(Edit|Reset)$/ })).toHaveCount(0);
    await expect(simRow.getByText("Edited · computed $25.00")).toBeVisible();

    // The Manager sees the same edited total in the queue and on the Dashboard card, and the
    // per-SIM line with its marker on the detail page.
    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const subject = `${clientName} — ${SEEDED_AGENT_NAME}`;
    await approveOtherWaitingClientInvoices(page, subject);
    await expect(page.getByRole("row", { name: new RegExp(subject) }).getByText("$76.40", { exact: true })).toBeVisible();

    await page.goto("/manager");
    const card = page.getByRole("list", { name: "Pending approvals" });
    await expect(card.getByRole("link", { name: new RegExp(subject) }).getByText("$76.40", { exact: true })).toBeVisible();
    await card.getByRole("link", { name: new RegExp(subject) }).click();

    await expect(page).toHaveURL(/\/manager\/invoices\/client\/.+/);
    await expect(page.getByRole("heading", { name: subject })).toBeVisible();
    const simLines = page.getByRole("list", { name: "Postpaid SIM Cards" });
    const managerSimRow = simLines.getByRole("listitem").filter({ hasText: simNumber });
    await expect(managerSimRow.getByText("$31.40", { exact: true })).toBeVisible();
    await expect(managerSimRow.getByText("Edited · computed $25.00")).toBeVisible();
    await expect(page.getByText("Edited · computed")).toHaveCount(1);
    await expect(page.getByText("$76.40", { exact: true }).first()).toBeVisible();

    // Mobile breakpoint: the lines and markers stay within the viewport.
    await page.setViewportSize({ width: 375, height: 800 });
    await expect(managerSimRow.getByText("Edited · computed $25.00")).toBeInViewport({ ratio: 1 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
});
