import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addSmartphone,
  addTester,
  approveFromPendingRequests,
  createClientAndContractWithSeededAgent,
  completeReturnRequest,
  fetchSmartphones,
  login,
  logout,
  pendingRequestRow,
  selectContractInSwitcher,
  submitRequestAsTester,
} from "./helpers";

/**
 * Kept in Stock: the Manager keeps a returned company-owned Smartphone in the Contract's Agent's
 * Stock instead of posting it back to the company (agent-stock ticket, spec.md Solution's Agent
 * Stock; Kept-in-Stock row of Disposition/Completion), driven against a real backend + Postgres
 * (see playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing decisions.
 * Mirrors tests/e2e/manager-decides-return-disposition.spec.ts's pattern almost exactly, choosing
 * Kept in Stock instead of Posted to company/Cancelled. The seeded agent@example.com login
 * resolves to the "Jordan Ellis" Agent (V5 migration), USD.
 */
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("agent stock", () => {
  test("the manager keeps a returned smartphone in stock; after completion the agent's stock page lists it and the fleet doesn't", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Cascade Field Ops ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);

    const model = "Pixel 8";
    const serial = `SN-${RUN_ID}`;
    await addSmartphone(page, contractId, model, serial);

    const testerEmail = `remy.faure+${RUN_ID}@cascade.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Return", { returnedUnitLabels: [`${model} — ${serial}`] });

    // A Return naming a company-owned unit is Pending Approval (return-client-owned-smartphones AC).
    await expect(page.getByRole("row", { name: /Return/ })).toContainText("Pending Approval");

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/requests");
    const pendingRow = pendingRequestRow(page, clientName, "Return");
    await expect(pendingRow).toContainText(clientName);

    // The Manager picks Kept in Stock instead of the preselected Posted to company (ticket AC:
    // "The Manager can choose Kept in Stock for a company-owned Smartphone ... when approving").
    await expect(pendingRow.getByLabel(new RegExp(model))).toHaveValue("POSTED_TO_COMPANY");
    await approveFromPendingRequests(page, clientName, "Return", { [model]: "Kept in Stock" });

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Return/ });
    await expect(row).toContainText("Kept in Stock");
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");
    await completeReturnRequest(page, row);

    // The Fleet loses it entirely (ticket AC: "appears on no Fleet") — API-level, mirroring
    // return-client-owned-smartphones.spec.ts's own precedent.
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);
    const smartphones = await fetchSmartphones(page, contractId);
    expect(smartphones.some((phone) => phone.serial === serial)).toBe(false);

    // The Agent's own Stock page lists it (ticket AC) — scoped by serial (unique per run), since
    // the Stock page can carry rows from other test runs/specs sharing this seeded Agent.
    await page.goto("/agent/stock");
    const stockRow = page.getByRole("row", { name: new RegExp(serial) });
    await expect(stockRow).toBeVisible();
    await expect(stockRow).toContainText(model);
    await expect(stockRow).toContainText(clientName);
  });

  // fulfil-from-stock ticket: extends the Stock e2e per its own Tests section ("extend the Stock
  // e2e: the Agent completes a Provision Smartphone from Stock and the Fleet shows that
  // Smartphone"). Builds its own Stock unit first (the same Return → Kept in Stock flow the test
  // above already covers end to end) rather than depending on test execution order.
  test("the agent fulfils a Provision Smartphone from Stock; the Fleet gains it and the Stock page loses it", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Beacon Field Services ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);

    const model = "Pixel 7";
    const serial = `SN-STOCK-${RUN_ID}`;
    await addSmartphone(page, contractId, model, serial);

    const testerEmail = `dara.iwu+${RUN_ID}@beacon.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    // Return the company-owned Smartphone, kept in Stock (same flow as the test above).
    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Return", { returnedUnitLabels: [`${model} — ${serial}`] });

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await approveFromPendingRequests(page, clientName, "Return", { [model]: "Kept in Stock" });

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);
    let row = page.getByRole("row", { name: /Return/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await completeReturnRequest(page, row);

    // Now submit a fresh Provision Smartphone Request on the same Contract, and fulfil it from
    // the Stock unit the Return above just created.
    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Provision Smartphone", { requestedModel: "Galaxy S24" });

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await approveFromPendingRequests(page, clientName, "Provision Smartphone");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);
    row = page.getByRole("row", { name: /Provision Smartphone/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();

    // The "from my Stock" picker offers the unit the earlier Return just kept (ticket AC: "offers
    // a 'from my Stock' picker only when a matching unit exists").
    await row.getByLabel(/From my Stock/).selectOption({ label: `${model} — ${serial}` });
    // The Fee amount field is required whenever a Provision Smartphone completes (it's fee
    // capable) and has a min="0.01" — 0 fails native HTML validation and silently blocks submit.
    await row.getByLabel(/Fee amount/).fill("0.01");
    const [statusResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/status") && resp.request().method() === "PATCH"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(statusResponse.status()).toBe(200);
    await expect(row.getByText("Completed", { exact: true })).toBeVisible();

    // The Fleet gains the Stock Smartphone, Active, at its original serial (ticket AC: "the unit
    // leaves the Stock page and appears in the Fleet") — API-level, mirroring the precedent above.
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);
    const smartphones = await fetchSmartphones(page, contractId);
    const fulfilled = smartphones.find((phone) => phone.serial === serial);
    expect(fulfilled?.status).toBe("ACTIVE");

    // It's gone from the Agent's own Stock page.
    await page.goto("/agent/stock");
    await expect(page.getByRole("row", { name: new RegExp(serial) })).not.toBeVisible();
  });
});
