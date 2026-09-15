import { test, expect, type Page } from "@playwright/test";

/**
 * An Agent opens a Contract's current-month draft Client Invoice, sees its live base amount and
 * Fee lines "at a glance", and attaches a carrier invoice file (client-invoice-generation ticket,
 * user stories 22-23), driven against a real backend + Postgres (see playwright.e2e.config.ts), at
 * the accessibility-tree level per spec.md's Testing decisions. Mirrors
 * tests/e2e/fee-logging-and-provisioning.spec.ts's pattern. The seeded agent@example.com login
 * resolves to the "Jordan Ellis" Agent (V5 migration), USD.
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

/** Creates a fresh Client, then a Contract linking it to the seeded Agent. Returns both ids. */
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

/** Adds a Tester under an existing Client (from its manager detail page) and returns their email. */
async function addTester(page: Page, clientId: string, email: string, password: string) {
  await page.goto(`/manager/clients/${clientId}`);
  await page.getByRole("button", { name: "Add tester" }).first().click();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Temporary password").fill(password);
  await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
  await expect(page.getByRole("cell", { name: email })).toBeVisible();
}

/** Adds a Postpaid SIM card to a Contract's Fleet from the Manager's Contract detail page. */
async function addPostpaidSimCard(page: Page, contractId: string, number: string, monthlyFee: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add SIM card" }).first().click();
  await page.getByLabel("Number").fill(number);
  await page.getByLabel("Flavor").selectOption({ label: "Postpaid" });
  await page.getByLabel(/Monthly fee/).fill(monthlyFee);
  await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
  await expect(page.getByRole("cell", { name: number })).toBeVisible();
}

/** Selects the Contract matching `clientName` in a Contract switcher, if more than one exists. */
async function selectContractInSwitcher(page: Page, clientName: string) {
  const trigger = page.locator('[aria-haspopup="listbox"]');
  if ((await trigger.count()) > 0) {
    await trigger.click();
    await page.getByRole("option", { name: new RegExp(clientName) }).click();
  }
}

async function submitRequestAsTester(page: Page, requestTypeLabel: string) {
  await page.goto("/client/requests");
  await page.getByRole("button", { name: "Submit Request" }).first().click();
  await page.getByLabel("Request type").selectOption({ label: requestTypeLabel });
  await page.getByRole("dialog").getByRole("button", { name: "Submit Request" }).click();
  await expect(page.getByText("Request submitted")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();
}

// Date.now() alone can collide across spec files: Playwright's collection phase can import
// several spec files within the same millisecond (see fee-logging-and-provisioning.spec.ts's
// note), so this suite uses its own RUN_ID rather than sharing one.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("client invoice generation", () => {
  test("an agent views a contract's draft client invoice with correct amounts and attaches a file", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Aurora Retail Group ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    await addPostpaidSimCard(page, contractId, `+1-555-${RUN_ID.slice(-4)}`, "25.00");
    const testerEmail = `priya.raman+${RUN_ID}@aurora.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Topup");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Topup/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();
    await row.getByLabel(/Fee amount/).fill("45.00");
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);

    // Viewing the Client Invoice for the first time this month creates the draft as a side
    // effect (client-invoice-generation ticket's get-or-create mechanic) — there's no separate
    // "create" step to perform first.
    await page.goto("/agent/client-invoices");
    await selectContractInSwitcher(page, clientName);

    await expect(page.getByText("Draft")).toBeVisible();
    // Base amount: the one currently-Active Postpaid SIM's monthly fee. Fees this month: the
    // Topup fee just logged. Total: the sum of both — all three "at a glance" per the ticket's AC.
    await expect(page.getByText("Base amount", { exact: true })).toBeVisible();
    await expect(page.getByText("$25.00", { exact: true })).toBeVisible();
    // "$45.00" appears twice — once in the "Fees this month" summary figure, once as the Fee
    // line's own amount — both correct, so this only needs to confirm at least one renders.
    await expect(page.getByText("$45.00", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("$70.00", { exact: true })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Topup" })).toBeVisible();

    // Attach a carrier invoice file — no dedicated "Choose file" dialog, a visually-hidden native
    // input behind the styled "Attach carrier invoice" button (see
    // AttachCarrierInvoiceFileControl).
    await expect(page.getByText("No carrier invoice files attached yet.")).toBeVisible();
    const [uploadResponse] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes("/client-invoice/files") && resp.request().method() === "POST",
      ),
      page.getByLabel("Attach carrier invoice file").setInputFiles({
        name: "october-carrier-invoice.pdf",
        mimeType: "application/pdf",
        buffer: Buffer.from("not a real pdf"),
      }),
    ]);
    expect(uploadResponse.status()).toBe(201);

    await expect(page.getByText("october-carrier-invoice.pdf")).toBeVisible();
    await expect(page.getByRole("link", { name: "Download" })).toBeVisible();
  });
});
