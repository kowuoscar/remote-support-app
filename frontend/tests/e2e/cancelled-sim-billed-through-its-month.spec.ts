import { test, expect, type Page } from "@playwright/test";

/**
 * A cancelled Postpaid SIM is billed through the month of its cancellation date
 * (cancelled-sim-billed-through-its-month ticket, spec.md Solution's "Billing a cancelled
 * Postpaid SIM"; ticket AC "The draft Client Invoice view lists a cancelled SIM Card it still
 * bills, marked with its cancellation date"), driven against a real backend + Postgres, at the
 * accessibility-tree level per spec.md's Testing decisions. Extends the Return flow already
 * covered end to end by tests/e2e/manager-decides-return-disposition.spec.ts (mirrors its helpers
 * closely) with this ticket's own assertion: the draft Client Invoice for the current month still
 * shows the cancelled SIM Card's fee. The seeded agent@example.com login resolves to the "Jordan
 * Ellis" Agent (V5 migration), USD.
 */
const SEEDED_USERS = {
  manager: { username: "manager@example.com", password: "ChangeMe123!" },
  agent: { username: "agent@example.com", password: "AgentDemo123!" },
} as const;

const SEEDED_AGENT_LABEL = "Jordan Ellis · USD";

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

async function createClientAndContractWithSeededAgent(
  page: Page,
  clientName: string,
): Promise<{ clientId: string; contractId: string }> {
  await page.goto("/manager/clients");
  await page.getByRole("button", { name: "Add client" }).first().click();
  await page.getByLabel("Client name").fill(clientName);
  await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("cell", { name: clientName })).toBeVisible();
  const clientHref = await page.getByRole("link", { name: clientName }).getAttribute("href");
  const clientId = clientHref!.split("/").pop()!;

  await page.goto("/manager/contracts");
  await page.getByRole("button", { name: "Add contract" }).first().click();
  await page.getByLabel("Client").selectOption({ label: clientName });
  await page.getByLabel("Agent").selectOption({ label: SEEDED_AGENT_LABEL });
  await page.getByRole("dialog").getByRole("button", { name: "Add contract" }).click();
  await expect(page.getByRole("row", { name: new RegExp(clientName) })).toBeVisible();

  await page.getByRole("link", { name: clientName }).click();
  await expect(page).toHaveURL(/\/manager\/contracts\/.+/);
  const contractId = page.url().split("/").pop()!;

  return { clientId, contractId };
}

async function addTester(page: Page, clientId: string, email: string, password: string) {
  await page.goto(`/manager/clients/${clientId}`);
  await page.getByRole("button", { name: "Add tester" }).first().click();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Temporary password").fill(password);
  await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
  await expect(page.getByRole("cell", { name: email })).toBeVisible();
}

/** A Postpaid SIM Card on the seeded US Carrier "Verizon"'s "Unlimited Welcome" Plan ($65.00). */
async function addPostpaidSimCard(page: Page, contractId: string, number: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add SIM card" }).first().click();
  await page.getByLabel("Number").fill(number);
  await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
  await page.getByLabel("Flavor").selectOption("POSTPAID");
  await page.getByRole("combobox", { name: "Postpaid plan" }).selectOption({ label: "Unlimited Welcome" });
  await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
  await expect(page.getByRole("cell", { name: number })).toBeVisible();
}

async function selectContractInSwitcher(page: Page, clientName: string) {
  const trigger = page.locator('[aria-haspopup="listbox"]');
  if ((await trigger.count()) > 0) {
    await trigger.click();
    await page.getByRole("option", { name: new RegExp(clientName) }).click();
  }
}

function pendingRequestRow(page: Page, clientName: string, typeLabel: string) {
  return page.getByRole("row", { name: new RegExp(`${typeLabel}.*${clientName}`) });
}

/** Today's date as a bare ISO day, UTC — a cancellation effective today is always "on or after
 * this month's first day", so it always still bills the current month regardless of what day the
 * suite happens to run on. */
function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Mirrors `frontend/lib/format.ts`'s `formatLocalDate` — the Fleet tables and the draft Client
 * Invoice's Postpaid SIM table render a bare `LocalDate` through it (design-review finding on
 * this feature's finisher pass), not the raw ISO string this test used to assert.
 */
function formatLocalDate(localDate: string): string {
  return new Date(`${localDate}T00:00:00Z`).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  });
}

const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("cancelled sim billed through its month", () => {
  test("a cancelled postpaid sim card still shows on the draft client invoice for the current month, marked with its cancellation date", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Havenwood Supply ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);

    const number = `+1-555-${RUN_ID}`;
    await addPostpaidSimCard(page, contractId, number);

    const testerEmail = `ines.duarte+${RUN_ID}@havenwood.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await page.getByRole("button", { name: "Submit Request" }).first().click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Request type").selectOption({ label: "Return" });
    await dialog.getByRole("listbox", { name: "Units to return" }).selectOption([{ label: number }]);
    await dialog.getByRole("button", { name: "Submit Request" }).click();
    await expect(page.getByText("Request submitted")).toBeVisible();
    await page.getByRole("button", { name: "Close" }).click();

    // A SIM Card is always company-owned, so this Return waits at Pending Approval (spec.md
    // Solution's Approval table).
    await expect(page.getByRole("row", { name: /Return/ })).toContainText("Pending Approval");

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/requests");
    const pendingRow = pendingRequestRow(page, clientName, "Return");
    await expect(pendingRow).toContainText(clientName);
    // A SIM Card's only fitting Disposition today is Cancelled, preselected (ticket AC).
    await expect(pendingRow.getByLabel(new RegExp(number.replace(/\+/g, "\\+")))).toHaveValue("CANCELLED");

    await Promise.all([
      page.waitForResponse((resp) => /\/api\/requests\/.+\/approve$/.test(resp.url())),
      pendingRow.getByRole("button", { name: "Approve" }).click(),
    ]);
    await expect(page.getByRole("row", { name: new RegExp(clientName) })).not.toBeVisible();

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Return/ });
    await expect(row).toContainText("Cancelled");
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");

    const effectiveDate = todayIso();
    await row.getByRole("button", { name: "Mark Completed" }).click();
    const dateField = row.getByLabel(new RegExp(number.replace(/\+/g, "\\+")));
    await dateField.fill(effectiveDate);
    // Waits for the actual completion PATCH's response, not just the DOM settling — the
    // completing form's own submit button is also labelled "Mark Completed", so a bare
    // `toContainText("Completed")` here can pass on that still-open button's own label before
    // the request round-trips, letting a too-early navigation below read pre-completion data.
    await Promise.all([
      page.waitForResponse((resp) => /\/requests\/.+\/status$/.test(resp.url()) && resp.request().method() === "PATCH"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    await expect(row.getByText("Completed", { exact: true })).toBeVisible();

    // The AC under test: the draft Client Invoice for the current month still lists this SIM
    // Card, marked with its cancellation date, and its fee is still in the base amount — a
    // cancellation date of "today" is always on or after this month's first day.
    await page.goto("/agent/client-invoices");
    await selectContractInSwitcher(page, clientName);

    await expect(page.getByRole("heading", { name: "Postpaid SIM Cards" })).toBeVisible();
    const simRow = page.getByRole("row", { name: new RegExp(number.replace(/\+/g, "\\+")) });
    await expect(simRow).toContainText(`Cancelled ${formatLocalDate(effectiveDate)}`);
    await expect(page.getByText("Base amount", { exact: true })).toBeVisible();
    await expect(page.getByText("$65.00").first()).toBeVisible();
  });
});
