import { test, expect, type Locator, type Page } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  addTester,
  addTesterAndSubmitRequestAsAgent,
  createClientAndContractWithSeededAgent,
  login,
  logout,
  rollSeededAgentInvoiceIntoThePast,
  selectClientInvoiceContract,
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
    await selectClientInvoiceContract(page, clientName);
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
    await selectClientInvoiceContract(page, clientName);
    await feeRow.getByRole("button", { name: "Reset" }).click();
    await expect(feeRow.getByText(/Edited · computed/)).toBeHidden();
    await expect(page.getByText("$76.40", { exact: true })).toBeVisible();

    await page.getByRole("button", { name: "Send Client Invoice" }).click();
    await page.getByRole("button", { name: "Confirm send" }).click();
    await expect(page.getByTestId("client-invoice-card").getByText("Awaiting approval")).toBeVisible();
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
  // invoice-lines-layout: where things sit at 1280 (a table) and below it (one-column rows), and
  // that nothing scrolls sideways, with a 64-character SIM number and a 500-character Fee description.
  test("lines-lay-out-inline-at-1280-and-as-one-column-rows-below-it", async ({ page }) => {
    test.setTimeout(240_000);
    await rollSeededAgentInvoiceIntoThePast();

    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Laid Out Lines Client ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const shortNumber = `+1-555-${RUN_ID.slice(-4)}`;
    const longNumber = `+${RUN_ID.replace(/\D/g, "")}${"7".repeat(64)}`.slice(0, 64);
    await addPostpaidSimCard(page, contractId, shortNumber, "25.00");
    await addPostpaidSimCard(page, contractId, longNumber, "25.00");
    const testerEmail = `layout.lines+${RUN_ID}@aurora.example`;
    const testerPassword = await addTester(page, clientId, testerEmail);
    expect(testerPassword).not.toBe("");
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);
    await page.getByRole("button", { name: "Log a fee" }).click();
    const dialog = page.locator("dialog[open]");
    await dialog.getByLabel("Tester").selectOption({ label: testerEmail });
    await dialog.getByLabel("Fee type").selectOption({ label: "Other" });
    await dialog.getByLabel(/Amount/).fill("12.00");
    const longDescription = `Replaced the battery ${"and checked the screen ".repeat(22)}`.slice(0, 500);
    await dialog.getByLabel("Description").fill(longDescription);
    await dialog.getByRole("button", { name: "Log fee" }).click();
    await expect(page.getByRole("row", { name: /Other/ })).toContainText("Completed");

    const invoiceId = await page.evaluate(async (id) => {
      const response = await fetch(`/api/contracts/${id}/client-invoice`);
      return ((await response.json()) as { id: string }).id;
    }, contractId);
    const invoiceUrl = `/agent/client-invoices?contract=${contractId}`;
    const byIdUrl = `/agent/client-invoices/${invoiceId}`;

    const box = async (locator: Locator) => (await locator.boundingBox())!;
    const sameLine = (a: { y: number; height: number }, b: { y: number; height: number }) =>
      a.y < b.y + b.height && b.y < a.y + a.height;
    const noSidewaysScroll = () =>
      page.evaluate(() => {
        const lists = [...document.querySelectorAll('[role="table"]')].map((table) => table.parentElement!);
        return (
          document.documentElement.scrollWidth === window.innerWidth &&
          lists.length === 2 &&
          lists.every((list) => list.scrollWidth === list.clientWidth)
        );
      });

    // 1280: the amount and Edit share a line, the note sits under them, the amounts line up.
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto(invoiceUrl);
    const simRow = page.getByRole("row", { name: new RegExp(shortNumber.replace("+", "\\+")) });
    const longRow = page.getByRole("row", { name: new RegExp(longNumber.slice(0, 20).replace("+", "\\+")) });
    const feeRow = page.getByRole("row", { name: /Other/ });
    await editLine(page, simRow, "31.40");
    const amount = simRow.getByText("$31.40", { exact: true });
    const amountBox = await box(amount);
    expect(sameLine(amountBox, await box(simRow.getByRole("button", { name: "Edit" })))).toBe(true);
    expect(sameLine(amountBox, await box(simRow.getByRole("button", { name: "Reset" })))).toBe(true);
    const note = await box(simRow.getByText(/Edited · computed/));
    expect(note.y).toBeGreaterThanOrEqual(amountBox.y + amountBox.height - 1);
    const rightEdges = await Promise.all(
      [amount, longRow.getByText("$25.00", { exact: true }), feeRow.getByText("$12.00", { exact: true })].map(
        async (locator) => {
          const b = await box(locator);
          return Math.round(b.x + b.width);
        },
      ),
    );
    expect(new Set(rightEdges).size).toBe(1);

    // Editing: the field, Save and Cancel on one line, the refusal below; Escape and Cancel return to Edit.
    await longRow.getByRole("button", { name: "Edit" }).click();
    const field = page.getByRole("textbox", { name: /Billed amount for/ });
    await expect(field).toBeFocused();
    const fieldBox = await box(field);
    const saveBox = await box(page.getByRole("button", { name: "Save" }));
    const cancelBox = await box(page.getByRole("button", { name: "Cancel" }));
    expect(sameLine(fieldBox, saveBox) && sameLine(saveBox, cancelBox)).toBe(true);
    expect(saveBox.x).toBeLessThan(cancelBox.x);
    expect(fieldBox.height).toBeLessThanOrEqual(30);
    await field.fill("-5");
    await page.getByRole("button", { name: "Save" }).click();
    const alert = page.getByRole("alert");
    await expect(alert).toBeVisible();
    expect((await box(alert)).y).toBeGreaterThanOrEqual(fieldBox.y + fieldBox.height - 1);
    await expect(field).toHaveValue("-5");
    await expect(field).toBeFocused();
    await page.keyboard.press("Escape");
    await expect(longRow.getByRole("button", { name: "Edit" })).toBeFocused();
    await longRow.getByRole("button", { name: "Edit" }).click();
    await page.getByRole("button", { name: "Cancel" }).click();
    await expect(longRow.getByRole("button", { name: "Edit" })).toBeFocused();

    // Below 1280: nothing scrolls sideways on either page, long values included.
    for (const width of [1279, 1024, 768, 390, 375]) {
      await page.setViewportSize({ width, height: 900 });
      for (const url of [invoiceUrl, byIdUrl]) {
        await page.goto(url);
        await expect(page.getByRole("button", { name: "Edit" }).first()).toBeInViewport({ ratio: 1 });
        expect(await noSidewaysScroll(), `${url} at ${width}`).toBe(true);
      }
    }

    // 390: what and amount share line 1, the long values wrap inside their rows, an edit and a Reset work.
    await page.setViewportSize({ width: 390, height: 900 });
    await page.goto(invoiceUrl);
    const phoneSim = page.getByRole("row", { name: new RegExp(shortNumber.replace("+", "\\+")) });
    const phoneLong = page.getByRole("row", { name: new RegExp(longNumber.slice(0, 20).replace("+", "\\+")) });
    const phoneFee = page.getByRole("row", { name: /Other/ });
    expect(sameLine(await box(phoneSim.getByText(shortNumber, { exact: true })), await box(phoneSim.getByText("$31.40", { exact: true })))).toBe(true);
    expect(sameLine(await box(phoneFee.getByText("Other", { exact: true })), await box(phoneFee.getByText("$12.00", { exact: true })))).toBe(true);
    for (const row of [phoneSim, phoneLong, phoneFee]) {
      await row.getByRole("button", { name: "Edit" }).scrollIntoViewIfNeeded();
      await expect(row.getByRole("button", { name: "Edit" })).toBeInViewport({ ratio: 1 });
    }
    const longBox = await box(phoneLong);
    const longAmount = await box(phoneLong.getByText("$25.00", { exact: true }));
    expect(longAmount.x + longAmount.width).toBeLessThanOrEqual(longBox.x + longBox.width);
    await expect(phoneLong.getByText("$25.00", { exact: true })).toBeInViewport({ ratio: 1 });
    expect((await box(phoneLong.getByText(longNumber, { exact: true }))).height).toBeGreaterThan(30);
    const description = await box(phoneFee.getByText(longDescription, { exact: true }));
    const feeBox = await box(phoneFee);
    expect(description.x + description.width).toBeLessThanOrEqual(feeBox.x + feeBox.width);

    await editLine(page, phoneFee, "9.50");
    await expect(phoneFee.getByText("$9.50", { exact: true })).toBeVisible();
    await phoneFee.getByRole("button", { name: "Reset" }).click();
    await expect(phoneFee.getByText(/Edited · computed/)).toBeHidden();
    await expect(phoneFee.getByText("$12.00", { exact: true })).toBeVisible();
    expect(await noSidewaysScroll()).toBe(true);

    // 390 after send: no Edit or Reset, the note stays.
    await page.getByRole("button", { name: "Send Client Invoice" }).click();
    await page.getByRole("button", { name: "Confirm send" }).click();
    await expect(page.getByTestId("client-invoice-card").getByText("Awaiting approval")).toBeVisible();
    await expect(page.getByRole("button", { name: /^(Edit|Reset)$/ })).toHaveCount(0);
    await expect(phoneSim.getByText("Edited · computed $25.00")).toBeVisible();
    expect(await noSidewaysScroll()).toBe(true);
  });
});
