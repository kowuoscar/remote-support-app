import { test, expect } from "@playwright/test";
import { createContractViaDialog, logout, readRevealedPasswordAndClose, selectInSideList } from "./helpers";

/**
 * Manager entity setup (manager-entity-setup ticket), driven against a real backend + Postgres
 * (see playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing
 * decisions — roles and labels, not pixels. Credentials match the seeded users (backend V2/V3
 * migrations). Mirrors tests/e2e/login.spec.ts's pattern.
 */
const SEEDED_USERS = {
  manager: { username: "manager@example.com", password: "ChangeMe123!" },
  agent: { username: "agent@example.com", password: "AgentDemo123!" },
  tester: { username: "tester@example.com", password: "TesterDemo123!" },
} as const;

async function login(page: import("@playwright/test").Page, username: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
}

// A unique suffix per run so re-runs against a persistent dev database (reuseExistingServer)
// don't collide on the primary-contact-per-client constraint or a repeated username.
// Date.now() alone can collide across spec files: Playwright's collection phase can
// import several spec files within the same millisecond, and more than one file in this
// suite picks the same literal client name (e.g. "Aurora Retail Group") for its first
// test, so an exact RUN_ID match produces a real duplicate row, not just a slow test.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("manager entity setup", () => {
  test("manager creates a client, an agent and a contract, and sees them listed", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await expect(page).toHaveURL(/\/manager$/);

    const clientName = `Aurora Retail Group ${RUN_ID}`;
    const agentName = `Camille Duforet ${RUN_ID}`;

    // Create a Client. The trigger and the dialog's submit button share a name ("Add client");
    // `.first()` picks the trigger defensively (a closed <dialog> has no accessible presence, so
    // this is normally unambiguous, but the trigger is also first in DOM order either way).
    await page.goto("/manager/clients");
    await page.getByRole("button", { name: "Add client" }).first().click();
    await page.getByLabel("Client name").fill(clientName);
    await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
    await expect(page.getByRole("cell", { name: clientName })).toBeVisible();

    // Create an Agent — France derives EUR.
    await page.goto("/manager/agents");
    await page.getByRole("button", { name: "Add agent" }).first().click();
    await page.getByLabel("Agent name").fill(agentName);
    await page.getByLabel("Country").selectOption("FRANCE");
    await page.getByLabel("Standing monthly salary").fill("2400");
    const agentEmail = `camille.duforet+${RUN_ID}@agents.example`;
    await page.getByLabel("Email").fill(agentEmail);
    await expect(page.getByRole("dialog").getByText("EUR")).toBeVisible();
    await page.getByRole("dialog").getByRole("button", { name: "Add agent" }).click();
    const agentPassword = await readRevealedPasswordAndClose(page);
    await expect(page.getByRole("row", { name: new RegExp(agentName) })).toBeVisible();
    await expect(page.getByRole("row", { name: new RegExp(agentName) })).toContainText("EUR");

    // Create a Contract linking them — currency copied from the Agent. The one page selects it.
    const firstContractId = await createContractViaDialog(page, clientName, `${agentName} · EUR`);
    await expect(page.getByRole("navigation", { name: "Clients" }).getByRole("link", { name: clientName })).toHaveAttribute(
      "aria-current",
      "true",
    );
    await expect(page.getByRole("heading", { level: 2, name: clientName })).toBeVisible();
    // The Agent select is shown even for one Agent.
    const agentSelect = page.getByRole("combobox", { name: "Agent", exact: true });
    await expect(agentSelect.locator("option")).toHaveText([agentName]);
    await expect(page.getByText("France · EUR")).toBeVisible();

    // The subtitle still counts Contracts.
    await expect(page.getByText(/\d+ in this tenant/)).toBeVisible();

    // A second Contract of the same Client, with the seeded Agent: the Agent select switches between them,
    // and the Fleet reads of the Contract not selected are never requested by the browser.
    const fleetReads: string[] = [];
    page.on("request", (request) => {
      if (/\/api\/contracts\/[^/]+\/(smartphones|sim-cards)/.test(request.url())) fleetReads.push(request.url());
    });
    const secondContractId = await createContractViaDialog(page, clientName, "Jordan Ellis · USD");
    await expect(agentSelect.locator("option")).toHaveText([agentName, "Jordan Ellis"]);
    await expect(agentSelect).toHaveValue(secondContractId);
    await expect(page.getByText("United States · USD")).toBeVisible();

    await agentSelect.selectOption({ label: agentName });
    await expect(page).toHaveURL(new RegExp(`[?&]contract=${firstContractId}`));
    await expect(page.getByText("France · EUR")).toBeVisible();
    await expect(agentSelect).toHaveValue(firstContractId);
    await page.reload();
    await expect(agentSelect).toHaveValue(firstContractId);

    // The Client's row stands for its first Agent by name, even after the other Agent was selected.
    await agentSelect.selectOption({ label: "Jordan Ellis" });
    await expect(page).toHaveURL(new RegExp(`[?&]contract=${secondContractId}`));
    await selectInSideList(page, "Clients", clientName);
    await expect(page).toHaveURL(new RegExp(`[?&]contract=${firstContractId}`));
    await expect(agentSelect).toHaveValue(firstContractId);
    expect(fleetReads.filter((url) => url.includes(secondContractId) || url.includes(firstContractId))).toEqual([]);

    // The old Contract page lands on the one page with that Contract selected.
    await page.goto(`/manager/contracts/${secondContractId}`);
    await expect(page).toHaveURL(new RegExp(`/manager/contracts\\?contract=${secondContractId}$`));
    await expect(agentSelect).toHaveValue(secondContractId);

    // At 390px the Clients dropdown replaces the column, with no sideways scroll.
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(page.getByRole("navigation", { name: "Clients" })).toBeHidden();
    await expect(page.getByRole("button", { name: "New Contract" }).first()).toBeVisible();
    await expect(page.locator('[aria-haspopup="listbox"]')).toBeVisible();
    await expect(agentSelect).toBeVisible();
    await expect
      .poll(() => page.evaluate(() => document.documentElement.scrollWidth === window.innerWidth))
      .toBe(true);

    // The shown password is the one that works: the new Agent signs in with it.
    await logout(page);
    await login(page, agentEmail, agentPassword);
    await expect(page).toHaveURL(/\/agent$/);
  });

  test("manager adds a tester under a client, flagged as primary contact", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await expect(page).toHaveURL(/\/manager$/);

    const clientName = `Kessler & Vance LLP ${RUN_ID}`;
    await page.goto("/manager/clients");
    await page.getByRole("button", { name: "Add client" }).first().click();
    await page.getByLabel("Client name").fill(clientName);
    await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
    await page.getByRole("link", { name: clientName }).click();
    await expect(page).toHaveURL(/\/manager\/clients\/.+/);

    const testerEmail = `helena.voss+${RUN_ID}@kessler.example`;
    await page.getByRole("button", { name: "Add tester" }).first().click();
    await page.getByLabel("Email").fill(testerEmail);
    await page.getByLabel("Primary contact for this client").check();
    await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
    const testerPassword = await readRevealedPasswordAndClose(page);

    await expect(page.getByRole("cell", { name: testerEmail })).toBeVisible();
    await expect(page.getByText("Primary contact", { exact: true })).toBeVisible();

    // The shown password is the one that works: the new Tester signs in with it.
    await logout(page);
    await login(page, testerEmail, testerPassword);
    await expect(page).toHaveURL(/\/client$/);
  });

  test("the Add agent creation response is never cacheable", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await expect(page).toHaveURL(/\/manager$/);

    await page.goto("/manager/agents");
    await page.getByRole("button", { name: "Add agent" }).first().click();
    await page.getByLabel("Agent name").fill(`No Store Agent ${RUN_ID}`);
    await page.getByLabel("Country").selectOption("FRANCE");
    await page.getByLabel("Standing monthly salary").fill("2400");
    await page.getByLabel("Email").fill(`no.store.agent+${RUN_ID}@agents.example`);
    const [response] = await Promise.all([
      page.waitForResponse((r) => r.url().endsWith("/api/agents") && r.request().method() === "POST"),
      page.getByRole("dialog").getByRole("button", { name: "Add agent" }).click(),
    ]);

    expect(response.status()).toBe(201);
    expect(response.headers()["cache-control"]).toContain("no-store");
  });

  test("the Add tester creation response is never cacheable", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await expect(page).toHaveURL(/\/manager$/);

    await page.goto("/manager/clients");
    await page.getByRole("button", { name: "Add client" }).first().click();
    await page.getByLabel("Client name").fill(`No Store Client ${RUN_ID}`);
    await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
    await page.getByRole("link", { name: `No Store Client ${RUN_ID}` }).click();
    await expect(page).toHaveURL(/\/manager\/clients\/.+/);

    await page.getByRole("button", { name: "Add tester" }).first().click();
    await page.getByLabel("Email").fill(`no.store.tester+${RUN_ID}@client.example`);
    const [response] = await Promise.all([
      page.waitForResponse((r) => /\/api\/clients\/.+\/testers$/.test(r.url()) && r.request().method() === "POST"),
      page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click(),
    ]);

    expect(response.status()).toBe(201);
    expect(response.headers()["cache-control"]).toContain("no-store");
  });

  test("an agent session is rejected from a manager-only page and API route", async ({ page }) => {
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await expect(page).toHaveURL(/\/agent$/);

    await page.goto("/manager/clients");
    await expect(page).toHaveURL(/\/login/);

    // A real in-page fetch (not page.request, Playwright's separate HTTP client, which doesn't
    // carry the httpOnly session cookie the same way a browser's own fetch does) — the same
    // request the Agent's own browser would make if it hit this Manager-only route directly.
    const status = await page.evaluate(async () => {
      const response = await fetch("/api/clients", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: "Should not be created" }),
      });
      return response.status;
    });
    expect(status).toBe(403);
  });

  test("a tester session is rejected from a manager-only page and API route", async ({ page }) => {
    await login(page, SEEDED_USERS.tester.username, SEEDED_USERS.tester.password);
    await expect(page).toHaveURL(/\/client$/);

    await page.goto("/manager/agents");
    await expect(page).toHaveURL(/\/login/);

    const status = await page.evaluate(async () => {
      const response = await fetch("/api/agents", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: "Should not be created",
          country: "FRANCE",
          salaryAmount: 100,
          username: "should.not.be.created@agents.example",
        }),
      });
      return response.status;
    });
    expect(status).toBe(403);
  });
});
