import { test, expect } from "@playwright/test";
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
 * A Manager sends a Client Invoice back and the Agent corrects and resends it (send-a-client-invoice-
 * back feature, user stories 1-5, 9-14, 17-22, 34), driven against a real backend + Postgres at the
 * accessibility-tree level. Prior art: client-invoice-submission-and-visibility.spec.ts and
 * edit-client-invoice-lines.spec.ts. A past month is not played here (spec Testing decisions 5).
 */
const SEEDED_AGENT_NAME = "Jordan Ellis";
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
const REASON = `The base amount is wrong, please recheck it. ${RUN_ID}`;

test.describe("send a client invoice back", () => {
  test("manager-sends-back-agent-corrects-and-resends-manager-approves", async ({ page }) => {
    await rollSeededAgentInvoiceIntoThePast();

    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Sent Back Client ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const simNumber = `+1-555-${RUN_ID.slice(-4)}`;
    await addPostpaidSimCard(page, contractId, simNumber, "25.00");
    // The Tester's Topup Request stays open: its Fee is logged only after the send-back.
    await addTesterAndSubmitRequestAsAgent(
      page,
      clientId,
      clientName,
      `send.back+${RUN_ID}@aurora.example`,
      "Topup",
      { simCardOptionLabel: `${simNumber} — Verizon` },
    );

    // The Agent sends the invoice as it stands: the one Postpaid SIM line, $25.00.
    await page.goto("/agent/client-invoices");
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByRole("heading", { name: "Sent back to you" })).toHaveCount(0);
    await page.getByRole("button", { name: "Send Client Invoice" }).click();
    await page.getByRole("button", { name: "Confirm send" }).click();
    await expect(page.getByText("Awaiting approval")).toBeVisible();

    // The Manager opens it from the Review Queue and sends it back with a reason.
    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const subject = `${clientName} — ${SEEDED_AGENT_NAME}`;
    await page.goto("/manager/invoices");
    await page.getByRole("link", { name: new RegExp(`^Review Client Invoice: ${subject},`) }).click();
    await expect(page).toHaveURL(/\/manager\/invoices\/client\/.+/);
    await page.getByRole("button", { name: "Send back" }).click();
    await page.getByRole("textbox", { name: "Reason for sending back" }).fill(REASON);
    await page.getByRole("button", { name: "Confirm send back" }).click();
    await expect(page.getByText(/Sent back to the Agent on/)).toBeVisible();
    await expect(page.getByText(REASON)).toBeVisible();

    await page.goto("/manager/invoices");
    await expect(page.getByRole("tablist", { name: "Filter the Review Queue by invoice type" })).toBeVisible();
    await expect(page.getByRole("link", { name: new RegExp(`^Review Client Invoice: ${subject},`) })).toHaveCount(0);

    // The Agent logs a Fee of the month, then finds the invoice under "Sent back to you".
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);
    const requestRow = page.getByRole("row", { name: /Topup/ });
    await requestRow.getByRole("button", { name: "Mark In Progress" }).click();
    await requestRow.getByRole("button", { name: "Mark Completed" }).click();
    await requestRow.getByLabel(/Fee amount/).fill("45.00");
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      requestRow.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);

    await page.goto("/agent/client-invoices");
    const section = page.getByRole("region", { name: "Sent back to you" });
    await expect(section).toBeVisible();
    const sentBackRow = section.getByRole("row", { name: new RegExp(clientName) });
    await expect(sentBackRow.getByText(REASON)).toBeVisible();

    // Mobile breakpoint: the page does not overflow the viewport (the table scrolls inside its own container).
    await page.setViewportSize({ width: 375, height: 800 });
    // Polled: the layout settles asynchronously after the viewport changes.
    await expect
      .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
      .toBe(true);

    // By keyboard alone: focus Open (it takes the visible focus ring) and press Enter.
    const open = sentBackRow.getByRole("link", { name: /^Open / });
    await open.focus();
    await expect(open).toBeFocused();
    await expect(open).toHaveCSS("outline-style", "solid");
    await page.keyboard.press("Enter");

    // By id: the amounts as sent, plus the late Fee as a new line.
    await expect(page).toHaveURL(/\/agent\/client-invoices\/.+/);
    await page.setViewportSize({ width: 1280, height: 800 });
    await expect(page.getByText("Sent back", { exact: true })).toBeVisible();
    await expect(page.getByText(REASON)).toBeVisible();
    const simRow = page.getByRole("row", { name: new RegExp(simNumber.replace("+", "\\+")) });
    const feeRow = page.getByRole("row", { name: /Topup/ });
    await expect(simRow.getByText("$25.00", { exact: true })).toBeVisible();
    await expect(feeRow.getByText("$45.00", { exact: true })).toBeVisible();

    // Correct the Postpaid SIM line and resend.
    await simRow.getByRole("button", { name: "Edit" }).click();
    const field = page.getByRole("textbox", { name: /Billed amount for/ });
    await field.fill("31.40");
    await page.getByRole("button", { name: "Save" }).click();
    await expect(field).toBeHidden();
    await expect(simRow.getByText("Edited · computed $25.00")).toBeVisible();
    await page.getByRole("button", { name: "Send Client Invoice" }).click();
    await page.getByRole("button", { name: "Confirm send" }).click();
    await expect(page.getByText("Awaiting approval")).toBeVisible();

    // Resent: it has left the Agent's "Sent back to you" list.
    await page.goto("/agent/client-invoices");
    await expect(page.getByRole("heading", { name: "Sent back to you" })).toHaveCount(0);

    // The Manager sees it back in the queue with the late Fee, the edited line and the earlier reason.
    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/invoices");
    await page.getByRole("link", { name: new RegExp(`^Review Client Invoice: ${subject},`) }).click();
    await expect(page).toHaveURL(/\/manager\/invoices\/client\/.+/);
    const simLines = page.getByRole("list", { name: "Postpaid SIM Cards" });
    const managerSimRow = simLines.getByRole("listitem").filter({ hasText: simNumber });
    await expect(managerSimRow.getByText("$31.40", { exact: true })).toBeVisible();
    await expect(managerSimRow.getByText("Edited · computed $25.00")).toBeVisible();
    await expect(page.getByText("$45.00", { exact: true }).first()).toBeVisible();
    await expect(page.getByText(/Previously sent back on .*:/)).toContainText(REASON);

    await page.getByRole("button", { name: "Approve" }).click();
    await expect(page.getByText("Approved", { exact: true })).toBeVisible();
  });
});
