import { test, expect, type Locator, type Page } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  addTesterAndSubmitRequestAsAgent,
  createClientAndContractWithSeededAgent,
  login,
  rollSeededAgentInvoiceIntoThePast,
  selectContractInSwitcher,
} from "./helpers";

/**
 * An Agent edits the amounts a draft Client Invoice bills, and resets one (edit-client-invoice-
 * lines ticket `agent-edits-lines-on-the-client-invoice-page`, user stories 2-6, 11, 14), driven
 * against a real backend + Postgres at the accessibility-tree level. Prior art:
 * client-invoice-generation.spec.ts ($25.00 base, $45.00 Fee, $70.00 total). The Manager's closing
 * steps of this journey belong to `manager-sees-edited-client-invoice-lines`.
 */
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

async function readLocalSupportFees(page: Page): Promise<number> {
  await page.goto("/agent/my-invoice");
  const fees = page.locator("dt", { hasText: /^Local Support Fees$/ }).locator("xpath=following-sibling::dd[1]");
  await expect(fees).toBeVisible();
  return Number((await fees.textContent())!.replace(/[^0-9.-]/g, ""));
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
  });
});
