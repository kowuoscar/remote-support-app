import { test, expect, type Locator, type Page } from "@playwright/test";
import {
  SEEDED_USERS,
  addSmartphone,
  addTester,
  addTesterAndSubmitRequestAsAgent,
  createClientAndContractWithSeededAgent,
  fetchFees,
  login,
  logout,
  selectContractInSwitcher,
  submitRequestAsTester,
} from "./helpers";

/**
 * Fee logging, its traceability rule, and the provisioning side-effect on Fleet
 * (fee-logging-and-provisioning ticket), driven against a real backend + Postgres (see
 * playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing decisions.
 * Mirrors tests/e2e/agent-request-fulfillment.spec.ts's pattern. The seeded agent@example.com
 * login resolves to the "Jordan Ellis" Agent (V5 migration), USD.
 */

/**
 * Sets up a Contract with a Tester, submits a Provision SIM request as them, and — as the Agent —
 * opens the matching row through "Mark In Progress" and a first "Mark Completed" click (which
 * reveals the completion form, still unsubmitted). Shared by the two Provision SIM tests below,
 * which only differ from here on in the SIM's flavor and the assertions that follow.
 */
async function beginProvisionSimCompletion(page: Page, clientName: string, testerEmail: string) {
  await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
  const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);
  await addTester(page, clientId, testerEmail, "Passw0rd!23");

  await logout(page);
  await login(page, testerEmail, "Passw0rd!23");
  await submitRequestAsTester(page, "Provision SIM");

  await logout(page);
  await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
  await page.goto("/agent/requests");
  await selectContractInSwitcher(page, clientName);

  const row = page.getByRole("row", { name: /Provision SIM/ });
  await row.getByRole("button", { name: "Mark In Progress" }).click();
  await row.getByRole("button", { name: "Mark Completed" }).click();
  return row;
}

/**
 * Clicks "Mark Completed" on an already-open completion form, waits for the Fee POST it fires,
 * asserts it succeeded and the row shows Completed, then navigates to the Agent's Fleet page for
 * this Contract. Shared by both Provision SIM tests, which only differ in what they assert there.
 */
async function completeAndGoToFleet(page: Page, row: Locator, clientName: string) {
  const [feeResponse] = await Promise.all([
    page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
    row.getByRole("button", { name: "Mark Completed" }).click(),
  ]);
  expect(feeResponse.status()).toBe(201);
  await expect(row).toContainText("Completed");

  await page.goto("/agent/fleet");
  await selectContractInSwitcher(page, clientName);
}

/** POSTs a Fee for `requestId` and returns the response status — used to probe the 400 boundary. */
async function postFeeStatus(page: Page, contractId: string, requestId: string, feeType: string, amount: number) {
  return page.evaluate(
    async ({ contractId, requestId, feeType, amount }) => {
      const response = await fetch(`/api/contracts/${contractId}/fees`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ requestId, feeType, amount }),
      });
      return response.status;
    },
    { contractId, requestId, feeType, amount },
  );
}

// Date.now() alone can collide across spec files: Playwright's collection phase can
// import several spec files within the same millisecond, and more than one file in this
// suite picks the same literal client name (e.g. "Aurora Retail Group") for its first
// test, so an exact RUN_ID match produces a real duplicate row, not just a slow test.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("fee logging and provisioning", () => {
  test("an agent logs a fee against an existing request while completing it", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Aurora Retail Group ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `priya.raman+${RUN_ID}@aurora.example`;
    await addTesterAndSubmitRequestAsAgent(page, clientId, clientName, testerEmail, "Topup");

    const row = page.getByRole("row", { name: /Topup/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");

    await row.getByRole("button", { name: "Mark Completed" }).click();
    await row.getByLabel(/Fee amount/).fill("45.00");
    // Completing this way fires two sequential requests (status PATCH, then Fee POST) before the
    // row re-renders — wait for the Fee POST itself to resolve rather than only the row's text,
    // so the assertion below can't race ahead of it.
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);
    await expect(row).toContainText("Completed");

    const fees = await fetchFees(page, contractId);
    expect(fees).toHaveLength(1);
    expect(fees[0].feeType).toBe("TOPUP");
    expect(fees[0].amount).toBe(45);
  });

  test("an agent logs a proactive fee with no pre-existing request", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Meridian Logistics ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `owen.reyes+${RUN_ID}@meridian.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    await page.getByRole("button", { name: "Log a fee" }).click();
    // Both the "Log a request" and "Log a fee" <dialog> elements exist in the DOM at once (only
    // one is open at a time) — scope to the open one so a shared label like "Tester" doesn't
    // resolve to two elements (LogRequestDialog also has a Tester field).
    const dialog = page.locator("dialog[open]");
    await dialog.getByLabel("Tester").selectOption({ label: testerEmail });
    await dialog.getByLabel("Fee type").selectOption({ label: "Repair" });
    await dialog.getByLabel(/Amount/).fill("60.00");
    await dialog.getByLabel("Description (optional)").fill("On-site battery replacement");
    await dialog.getByRole("button", { name: "Log fee" }).click();

    // The linking Request was auto-created, already Completed, agent-authored.
    const row = page.getByRole("row", { name: /Repair/ });
    await expect(row).toContainText("Completed");
    await expect(row).toContainText(testerEmail);
    await expect(row).toContainText(`Logged by ${SEEDED_USERS.agent.username}`);

    const fees = await fetchFees(page, contractId);
    expect(fees).toHaveLength(1);
    expect(fees[0].feeType).toBe("REPAIR");
    expect(fees[0].amount).toBe(60);
  });

  test("completing a provision smartphone request adds a fleet item and retires the replaced one", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Bright Path Clinics ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const oldSerial = `SN-OLD-${RUN_ID}`;
    await addSmartphone(page, contractId, "iPhone 13", oldSerial);
    const testerEmail = `marco.diaz+${RUN_ID}@brightpath.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Provision Smartphone");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Provision Smartphone/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();

    const newSerial = `SN-NEW-${RUN_ID}`;
    await row.getByLabel(/Fee amount/).fill("150.00");
    await row.getByLabel("New smartphone model").fill("iPhone 15");
    await row.getByLabel("New smartphone serial").fill(newSerial);
    await row.getByLabel(/Retiring which smartphone/).selectOption({ label: `iPhone 13 — ${oldSerial}` });
    // Completing this way fires two sequential requests (status PATCH, then Fee POST) before the
    // row re-renders — wait for the Fee POST itself to resolve rather than only the row's text.
    // Regression (fleet-management): the Fleet view reflects both the addition and the retirement.
    await completeAndGoToFleet(page, row, clientName);
    await expect(page.getByRole("row", { name: new RegExp(oldSerial) })).toContainText("Retired");
    await expect(page.getByRole("row", { name: new RegExp(newSerial) })).toContainText("Active");
  });

  test("an agent completes a provision SIM request by picking a carrier, and the fleet shows it", async ({
    page,
  }) => {
    // sim-card-carrier ticket: the Carrier comes from the catalog, never free text.
    const clientName = `Solene Cosmetics ${RUN_ID}`;
    const testerEmail = `ines.moreau+${RUN_ID}@solene.example`;
    const row = await beginProvisionSimCompletion(page, clientName, testerEmail);

    const number = `+1-555-${RUN_ID}`;
    await row.getByLabel(/Fee amount/).fill("15.00");
    await row.getByLabel("New SIM number").fill(number);
    const carrier = row.getByRole("combobox", { name: "Carrier" });
    // Archived Carriers are never offered: the seeded Sprint is archived.
    await expect(carrier.getByRole("option", { name: "Sprint" })).toHaveCount(0);
    await carrier.selectOption({ label: "Verizon" });
    await row.getByLabel("Flavor").selectOption({ label: "Prepaid" });
    await completeAndGoToFleet(page, row, clientName);
    await expect(page.getByRole("row", { name: new RegExp(number.replace(/\+/g, "\\+")) })).toContainText(
      "Verizon",
    );
  });

  test("an agent completes a provision SIM request for a postpaid SIM by picking a carrier and a plan", async ({
    page,
  }) => {
    // postpaid-sim-plan ticket: the monthly fee is the Plan's price, never typed.
    const clientName = `Lumen Health Labs ${RUN_ID}`;
    const testerEmail = `noor.hassan+${RUN_ID}@lumenhealth.example`;
    const row = await beginProvisionSimCompletion(page, clientName, testerEmail);

    const number = `+1-555-pp-${RUN_ID}`;
    await row.getByLabel(/Fee amount/).fill("15.00");
    await row.getByLabel("New SIM number").fill(number);
    await row.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await row.getByLabel("Flavor").selectOption({ label: "Postpaid" });
    const plan = row.getByRole("combobox", { name: "Postpaid plan" });
    // Archived Plans are never offered: the seeded "Start Unlimited" is archived.
    await expect(plan.getByRole("option", { name: "Start Unlimited" })).toHaveCount(0);
    await plan.selectOption({ label: "Unlimited Welcome" });
    // The Plan sets the fee, read-only, before the SIM Card even exists.
    await expect(row).toContainText("$65.00");

    await completeAndGoToFleet(page, row, clientName);
    const simRow = page.getByRole("row", { name: new RegExp(number.replace(/\+/g, "\\+")) });
    await expect(simRow).toContainText("Unlimited Welcome");
    await expect(simRow).toContainText("$65.00");
  });

  test("a reboot or a like-for-like sim swap can never carry a fee", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Harbor & Finch Realty ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `charlotte.finch+${RUN_ID}@harborfinch.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Reboot");
    await submitRequestAsTester(page, "SIM Swap");

    const requestIds = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`);
      const body = (await response.json()) as { id: string; type: string }[];
      return {
        reboot: body.find((r) => r.type === "REBOOT")!.id,
        simSwap: body.find((r) => r.type === "SIM_SWAP")!.id,
      };
    }, contractId);

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    // No UI path even offers a fee against these types (RequestStatusControl only shows the fee
    // form for a type that can carry one) — the boundary itself is verified directly at the API,
    // the same way the equivalent access-control boundaries are in agent-request-fulfillment's
    // suite.
    const rebootStatus = await postFeeStatus(page, contractId, requestIds.reboot, "TOPUP", 10);
    expect(rebootStatus).toBe(400);

    const simSwapStatus = await postFeeStatus(page, contractId, requestIds.simSwap, "PROVISION_SIM", 15);
    expect(simSwapStatus).toBe(400);

    const fees = await fetchFees(page, contractId);
    expect(fees).toHaveLength(0);
  });
});
