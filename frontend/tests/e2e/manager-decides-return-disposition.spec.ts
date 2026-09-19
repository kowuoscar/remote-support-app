import { test, expect, type Page } from "@playwright/test";

/**
 * The Manager decides Dispositions for a Return holding company-owned units
 * (manager-decides-return-disposition ticket, spec.md Solution's Approval/Disposition/Completion
 * tables), driven against a real backend + Postgres (see playwright.e2e.config.ts), at the
 * accessibility-tree level per spec.md's Testing decisions. Mirrors
 * tests/e2e/return-client-owned-smartphones.spec.ts and
 * tests/e2e/manager-approves-requests.spec.ts's patterns. The seeded agent@example.com login
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

async function addTester(page: Page, clientId: string, email: string, password: string) {
  await page.goto(`/manager/clients/${clientId}`);
  await page.getByRole("button", { name: "Add tester" }).first().click();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Temporary password").fill(password);
  await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
  await expect(page.getByRole("cell", { name: email })).toBeVisible();
}

/** Adds a company-owned Smartphone (the default Owner — spec.md Solution: Fleet model). */
async function addCompanyOwnedSmartphone(page: Page, contractId: string, model: string, serial: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add smartphone" }).first().click();
  await page.getByLabel("Model").fill(model);
  await page.getByLabel("Serial").fill(serial);
  await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
  await expect(page.getByRole("cell", { name: serial })).toBeVisible();
}

async function addSimCard(page: Page, contractId: string, number: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add SIM card" }).first().click();
  await page.getByLabel("Number").fill(number);
  await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
  await page.getByLabel("Flavor").selectOption("PREPAID");
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

/** Scopes a Pending Requests page row by both type and Client name — see manager-approves-requests.spec.ts's own note. */
function pendingRequestRow(page: Page, clientName: string, typeLabel: string) {
  return page.getByRole("row", { name: new RegExp(`${typeLabel}.*${clientName}`) });
}

const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("manager decides return disposition", () => {
  test("a tester returns a company-owned smartphone and a sim card, the manager posts one and cancels the other, and the agent completes with a date", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Solstice Retail ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);

    const serial = `SN-${RUN_ID}`;
    await addCompanyOwnedSmartphone(page, contractId, "iPhone 15", serial);
    const number = `+1-555-${RUN_ID}`;
    await addSimCard(page, contractId, number);

    const testerEmail = `dara.novak+${RUN_ID}@solstice.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await page.getByRole("button", { name: "Submit Request" }).first().click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Request type").selectOption({ label: "Return" });
    await dialog
      .getByRole("listbox", { name: "Units to return" })
      .selectOption([{ label: `iPhone 15 — ${serial}` }, { label: number }]);
    await dialog.getByRole("button", { name: "Submit Request" }).click();
    await expect(page.getByText("Request submitted")).toBeVisible();
    await page.getByRole("button", { name: "Close" }).click();

    // A Return naming a company-owned unit is Pending Approval, not Submitted (ticket AC).
    await expect(page.getByRole("row", { name: /Return/ })).toContainText("Pending Approval");

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/requests");
    const pendingRow = pendingRequestRow(page, clientName, "Return");
    await expect(pendingRow).toContainText(clientName);

    // Both pickers are preselected to their one fitting choice today (ticket AC) — the manager
    // just confirms them, no need to change either select's value.
    await expect(pendingRow.getByLabel(new RegExp(`iPhone 15`))).toHaveValue("POSTED_TO_COMPANY");
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
    await expect(row).toContainText("Posted to company");
    await expect(row).toContainText("Cancelled");
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");

    // Completing asks for the cancelled SIM Card's effective date (ticket AC) — refused without one.
    await row.getByRole("button", { name: "Mark Completed" }).click();
    const dateField = row.getByLabel(new RegExp(number.replace(/\+/g, "\\+")));
    await expect(dateField).toHaveAttribute("type", "date");
    await dateField.fill("2026-08-15");
    await row.getByRole("button", { name: "Mark Completed" }).click();
    await expect(row).toContainText("Completed");

    // API-level assertions for the Fleet's post-completion state (mirrors
    // return-client-owned-smartphones.spec.ts's own precedent — text-content on a Fleet row is
    // the wrong tool once a Smartphone can also show an installed SIM's number).
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);

    const smartphones = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/smartphones`);
      return (await response.json()) as { id: string; serial: string; status: string }[];
    }, contractId);
    const returnedPhone = smartphones.find((phone) => phone.serial === serial)!;
    expect(returnedPhone.status).toBe("RETIRED");

    const simCards = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/sim-cards`);
      return (await response.json()) as {
        id: string;
        number: string;
        status: string;
        cancellationEffectiveDate?: string;
      }[];
    }, contractId);
    const returnedSim = simCards.find((sim) => sim.number === number)!;
    expect(returnedSim.status).toBe("RETIRED");
    expect(returnedSim.cancellationEffectiveDate).toBe("2026-08-15");
  });
});
