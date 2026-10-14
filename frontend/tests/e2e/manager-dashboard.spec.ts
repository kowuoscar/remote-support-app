import { test, expect, type Page } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  createClientAndContractWithSeededAgent,
  login,
  logout,
  rollSeededAgentInvoiceIntoThePast,
  selectContractInSwitcher,
} from "./helpers";

const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

/** The USD line of a money card in cents, or 0 while the card reads a plain 0. */
async function usdCents(page: Page, testId: string): Promise<number> {
  const text = await page.getByTestId(testId).innerText();
  const match = text.match(/\$([\d,]+\.\d{2})/);
  return match ? Math.round(Number(match[1].replace(/,/g, "")) * 100) : 0;
}

async function readMoneyCards(page: Page) {
  await page.goto("/manager");
  await expect(page.getByTestId("dashboard-ready")).toBeVisible();
  return { billed: await usdCents(page, "billed-stat"), payout: await usdCents(page, "payout-stat") };
}

/** The total the Review Queue shows for the waiting invoice whose subject matches, in cents. */
async function queuedTotalCents(page: Page, kind: "CLIENT_INVOICE" | "AGENT_INVOICE", clientName: string | null) {
  const items: { kind: string; clientName: string | null; agentName: string; totalAmount: number }[] =
    await page.evaluate(async () => (await fetch("/api/review-queue")).json());
  const item = items.find(
    (i) => i.kind === kind && (kind === "AGENT_INVOICE" ? i.agentName === "Jordan Ellis" : i.clientName === clientName),
  );
  expect(item, `a waiting ${kind}`).toBeDefined();
  return Math.round(item!.totalAmount * 100);
}

/**
 * The Manager dashboard against a real backend + Postgres (real-manager-dashboard spec; ticket
 * manager-dashboard-identity-and-counts). The e2e database is shared across specs, so the counts
 * are compared with the lists the API returns, never with absolute figures.
 */
test.describe("the Manager dashboard's identity and counts", () => {
  test("the chip names the signed-in login and the counts equal the Tenant's lists", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager");
    await expect(page.getByTestId("dashboard-ready")).toBeVisible();

    // The chip names the Manager by their name, else their login. The shared e2e Manager is unnamed
    // until the rename test below runs, and a name can't be cleared, so the expectation comes from
    // the same read the page makes, keeping this test independent of order and of earlier runs.
    const me: { username: string; name?: string } = await page.evaluate(async () =>
      (await fetch("/api/me")).json(),
    );
    expect(me.username).toBe(SEEDED_USERS.manager.username);
    await expect(page.getByTestId("viewer-menu-trigger")).toHaveText(`${me.name || me.username} · Manager`);
    await expect(page.getByText("Priya Ashford")).toHaveCount(0);

    // The Clients, Agents and Contracts pages count the same three list routes ("N in this tenant").
    const lengths: Record<string, number> = {};
    for (const [key, path] of [
      ["clients", "/manager/clients"],
      ["agents", "/manager/agents"],
      ["contracts", "/manager/contracts"],
    ] as const) {
      await page.goto(path);
      const subtitle = await page.getByText(/^\d+ in this tenant$/).textContent();
      lengths[key] = Number(subtitle!.split(" ")[0]);
    }
    await page.goto("/manager");
    await expect(page.getByTestId("dashboard-ready")).toBeVisible();

    await expect(page.getByTestId("clients-stat")).toContainText("In this tenant");
    // The figure itself is the card's second span (label, value, meta).
    for (const [testId, expected] of [
      ["clients-stat", lengths.clients],
      ["agents-stat", lengths.agents],
      ["contracts-stat", lengths.contracts],
    ] as const) {
      await expect(page.getByTestId(testId).locator(":scope > span").nth(1)).toHaveText(String(expected));
    }
  });

  test("a Manager changes their name from the account menu and the Dashboard chip shows it", async ({ page }) => {
    const newName = `Yazan Abuhassan ${Date.now()}`;
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager");
    await expect(page.getByTestId("dashboard-ready")).toBeVisible();

    // Every Manager page's menu: Change name, Change password, Log out.
    await page.getByTestId("viewer-menu-trigger").click();
    await expect(page.getByRole("menuitem")).toHaveText(["Change name", "Change password", "Log out"]);
    await page.getByRole("menuitem", { name: "Change name" }).click();

    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible();

    // A blank name is refused at the field.
    await dialog.getByLabel("Name").fill("");
    await dialog.getByRole("button", { name: "Change name" }).click();
    await expect(dialog.getByRole("alert")).toHaveText("Enter your name.");

    await dialog.getByLabel("Name").fill(newName);
    await dialog.getByRole("button", { name: "Change name" }).click();
    await expect(dialog).toBeHidden();

    // No sign-in in between: the chip shows the new name at once, and still after a reload.
    await expect(page.getByTestId("viewer-menu-trigger")).toHaveText(`${newName} · Manager`);
    await page.reload();
    await expect(page.getByTestId("viewer-menu-trigger")).toHaveText(`${newName} · Manager`);

    // The dialog reopens holding the saved name.
    await page.getByTestId("viewer-menu-trigger").click();
    await page.getByRole("menuitem", { name: "Change name" }).click();
    await expect(page.getByRole("dialog").getByLabel("Name")).toHaveValue(newName);

    // Other Manager pages keep the plain chip, with the same three-item menu.
    await page.goto("/manager/invoices");
    await expect(page.getByTestId("viewer-menu-trigger")).toHaveText("Manager");
    await page.getByTestId("viewer-menu-trigger").click();
    await expect(page.getByRole("menuitem")).toHaveText(["Change name", "Change password", "Log out"]);
  });
});

test.describe("the Manager dashboard's money cards", () => {
  test("Billed grows when the Agent sends a Client Invoice, not on approval; Payout grows on approval, not on send", async ({
    page,
  }) => {
    test.setTimeout(120_000);
    const clientName = `Money Dash ${RUN_ID}`;

    // A Contract with a Postpaid SIM, so the Client Invoice has a non-zero total.
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const { contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    await addPostpaidSimCard(page, contractId, `+1-555-${RUN_ID.slice(-4)}`, "37.53");
    const beforeBilled = await readMoneyCards(page);

    // The Agent sends the Client Invoice: Billed grows by exactly its total.
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/client-invoices");
    await selectContractInSwitcher(page, clientName);
    await page.getByRole("button", { name: "Send Client Invoice" }).click();
    await page.getByRole("button", { name: "Confirm send" }).click();
    await expect(page.getByText("Awaiting approval")).toBeVisible();
    await logout(page);

    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientTotal = await queuedTotalCents(page, "CLIENT_INVOICE", clientName);
    expect(clientTotal).toBeGreaterThan(0);
    const afterSend = await readMoneyCards(page);
    expect(afterSend.billed - beforeBilled.billed).toBe(clientTotal);
    expect(afterSend.payout).toBe(beforeBilled.payout);

    // The Manager approves it: Billed is unchanged.
    await page.goto("/manager/invoices");
    await page.getByRole("link", { name: new RegExp(`^Review Client Invoice: ${clientName} `) }).click();
    await page.getByRole("button", { name: "Approve" }).click();
    await expect(page.getByText("Approved", { exact: true })).toBeVisible();
    const afterApprove = await readMoneyCards(page);
    expect(afterApprove.billed).toBe(afterSend.billed);

    // The Agent sends their Agent Invoice: Payout is unchanged.
    await rollSeededAgentInvoiceIntoThePast();
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/my-invoice");
    await page.getByRole("button", { name: "Send Agent Invoice" }).click();
    await page.getByRole("button", { name: "Confirm send" }).click();
    await expect(page.getByText("Awaiting approval")).toBeVisible();
    await logout(page);

    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const afterAgentSend = await readMoneyCards(page);
    expect(afterAgentSend.payout).toBe(afterApprove.payout);

    // The Manager overrides the Salary (a total this run owns), approves, and Payout grows by it.
    await page.goto("/manager/invoices");
    await page.getByRole("link", { name: /^Review Agent Invoice: Jordan Ellis, / }).click();
    const salaryInput = page.getByLabel(/Override Salary/);
    const overrideForm = page.locator("form").filter({ has: salaryInput });
    await salaryInput.fill("4321.00");
    await overrideForm.getByRole("button", { name: "Override" }).click();
    await expect(overrideForm.getByText("Applied to this invoice only.")).toBeVisible();
    const agentTotal = await queuedTotalCents(page, "AGENT_INVOICE", null);
    await page.getByRole("button", { name: "Approve" }).click();
    await expect(page.getByText("Approved", { exact: true })).toBeVisible();

    const afterAgentApprove = await readMoneyCards(page);
    expect(afterAgentApprove.payout - afterAgentSend.payout).toBe(agentTotal);
    expect(afterAgentApprove.billed).toBe(afterApprove.billed);

    // Marking it paid leaves Payout unchanged.
    await page.goto("/manager/invoices");
    await page.getByRole("link", { name: /^Review Agent Invoice: Jordan Ellis, / }).click();
    await page.getByRole("button", { name: "Mark paid" }).click();
    await expect(page.getByText("Paid", { exact: true })).toBeVisible();
    expect((await readMoneyCards(page)).payout).toBe(afterAgentApprove.payout);
  });
});
