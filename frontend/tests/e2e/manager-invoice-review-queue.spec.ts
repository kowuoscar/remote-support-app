import { test, expect, type Page } from "@playwright/test";

/**
 * The Manager's Review Queue for Client Invoices end to end (manager-invoice-review-queue spec;
 * client-invoice-review-page ticket): an Agent sends a Client Invoice, the Manager opens it from
 * the Review Queue, approves it on its detail page, sees the final state, and finds it gone from
 * the queue. Business rules (ordering, past months, 403/404) are covered at the API seam.
 */
const SEEDED_USERS = {
  manager: { username: "manager@example.com", password: "ChangeMe123!" },
  agent: { username: "agent@example.com", password: "AgentDemo123!" },
} as const;

const SEEDED_AGENT_NAME = "Jordan Ellis";

async function login(page: Page, username: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

async function logout(page: Page) {
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login/);
}

async function createClientAndContractWithSeededAgent(page: Page, clientName: string) {
  await page.goto("/manager/clients");
  await page.getByRole("button", { name: "Add client" }).first().click();
  await page.getByLabel("Client name").fill(clientName);
  await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("cell", { name: clientName })).toBeVisible();

  await page.goto("/manager/contracts");
  await page.getByRole("button", { name: "Add contract" }).first().click();
  await page.getByLabel("Client").selectOption({ label: clientName });
  await page.getByLabel("Agent").selectOption({ label: `${SEEDED_AGENT_NAME} · USD` });
  await page.getByRole("dialog").getByRole("button", { name: "Add contract" }).click();
  await expect(page.getByRole("row", { name: new RegExp(clientName) })).toBeVisible();
}

async function selectContractInSwitcher(page: Page, clientName: string) {
  const trigger = page.locator('[aria-haspopup="listbox"]');
  if ((await trigger.count()) > 0) {
    await trigger.click();
    await page.getByRole("option", { name: new RegExp(clientName) }).click();
  }
}

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
});
