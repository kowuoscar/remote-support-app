import { test, expect, type Locator, type Page } from "@playwright/test";
import {
  SEEDED_USERS,
  addSimCard,
  addSmartphone,
  addTester,
  addTesterAndSubmitRequestAsAgent,
  approveFromPendingRequests,
  createClientAndContractWithSeededAgent,
  fetchFees,
  login,
  logout,
  selectContractInSwitcher,
  simCardsTable,
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
 * Clicks "Mark Completed" on an already-open completion form, waits for the Fee POST it fires,
 * asserts it succeeded and the row shows Completed, then navigates to the Agent's Fleet page for
 * this Contract. Shared by every test whose completion both logs a Fee and changes the Fleet.
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

/**
 * Approves a Pending Approval Request from the Manager, then opens the matching Agent row and
 * clicks through to "Mark Completed" (revealing the completion form, still unsubmitted). Shared
 * by the two Provision SIM tests below, which only differ from here on in the SIM's flavor and
 * the assertions that follow.
 */
async function approveAndBeginAgentCompletion(page: Page, clientName: string, typeLabel: string) {
  await logout(page);
  await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
  await approveFromPendingRequests(page, clientName, typeLabel);

  await logout(page);
  await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
  await page.goto("/agent/requests");
  await selectContractInSwitcher(page, clientName);

  const row = page.getByRole("row", { name: new RegExp(typeLabel) });
  await row.getByRole("button", { name: "Mark In Progress" }).click();
  await row.getByRole("button", { name: "Mark Completed" }).click();
  return row;
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
    const simNumber = `+1-555-${RUN_ID}`;
    await addSimCard(page, contractId, simNumber);
    const testerEmail = `priya.raman+${RUN_ID}@aurora.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Topup", { simCardOptionLabel: `${simNumber} — Verizon` });

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Topup/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");

    await row.getByRole("button", { name: "Mark Completed" }).click();
    await row.getByLabel(/Fee amount/).fill("45.00");
    await completeAndGoToFleet(page, row, clientName);

    const fees = await fetchFees(page, contractId);
    expect(fees).toHaveLength(1);
    expect(fees[0].feeType).toBe("TOPUP");
    expect(fees[0].amount).toBe(45);
  });

  // reboot-and-topup-details ticket: completing a Topup Request pre-fills the Fee amount from
  // its own Topup Option and links the Fee to it (still editable, just not overridden here).
  test("completing a topup request pre-fills the fee from its own topup option and links it", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Kessler & Vance LLP ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const simNumber = `+1-555-opt-${RUN_ID}`;
    await addSimCard(page, contractId, simNumber);
    const testerEmail = `helena.voss+${RUN_ID}@kessler.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    // Verizon's seeded "Prepaid Refill 35" Option, $35.00 (V22 migration).
    await submitRequestAsTester(page, "Topup", { simCardOptionLabel: `${simNumber} — Verizon` });

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Topup/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();

    // Pre-filled from the Request's own Topup Option — the Agent completes without changing it.
    await expect(row.getByLabel(/Fee amount/)).toHaveValue("35");
    await completeAndGoToFleet(page, row, clientName);

    const fees = await fetchFees(page, contractId);
    expect(fees).toHaveLength(1);
    expect(fees[0].feeType).toBe("TOPUP");
    expect(fees[0].amount).toBe(35);
    expect(fees[0].topupOptionId).toBeTruthy();
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
    await dialog.getByLabel("Fee type").selectOption({ label: "Other" });
    await dialog.getByLabel(/Amount/).fill("60.00");
    // Other requires a description, which the auto-created linking Request also carries.
    await dialog.getByLabel("Description").fill("On-site battery replacement");
    await dialog.getByRole("button", { name: "Log fee" }).click();

    // The linking Request was auto-created, already Completed, agent-authored.
    const row = page.getByRole("row", { name: /Other/ });
    await expect(row).toContainText("Completed");
    await expect(row).toContainText(testerEmail);
    await expect(row).toContainText(`Logged by ${SEEDED_USERS.agent.username}`);

    const fees = await fetchFees(page, contractId);
    expect(fees).toHaveLength(1);
    expect(fees[0].feeType).toBe("OTHER");
    expect(fees[0].amount).toBe(60);
  });

  // provision-request-details ticket: the requested model is now given at submission, and
  // completing needs no Agent input at all — Provision no longer retires a named unit (that's
  // what Replace is for).
  test("a tester names the requested model at submission, and completing needs no agent input", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Bright Path Clinics ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const oldSerial = `SN-OLD-${RUN_ID}`;
    await addSmartphone(page, contractId, "iPhone 13", oldSerial);
    const testerEmail = `marco.diaz+${RUN_ID}@brightpath.example`;

    await addTesterAndSubmitRequestAsAgent(page, clientId, clientName, testerEmail, "Provision Smartphone", {
      requestedModel: "iPhone 15",
    });

    const row = await approveAndBeginAgentCompletion(page, clientName, "Provision Smartphone");
    await expect(row.getByText("Adds")).toContainText("iPhone 15");

    await row.getByLabel(/Fee amount/).fill("150.00");
    await completeAndGoToFleet(page, row, clientName);

    // Regression (fleet-management): the Fleet view shows the new unit, and the older one
    // untouched — Provision no longer retires anything.
    await expect(page.getByRole("row", { name: new RegExp(oldSerial) })).toContainText("Active");
    await expect(page.getByRole("row", { name: "iPhone 15" })).toContainText("Active");
  });

  test("a tester names the carrier at submission, and the agent completes with only a number", async ({ page }) => {
    // sim-card-carrier ticket: the Carrier comes from the catalog, never free text.
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Solene Cosmetics ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `ines.moreau+${RUN_ID}@solene.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await page.getByRole("button", { name: "Submit Request" }).first().click();
    const submitDialog = page.getByRole("dialog");
    await submitDialog.getByLabel("Request type").selectOption({ label: "Provision SIM" });
    const carrierPicker = submitDialog.getByRole("combobox", { name: "Carrier" });
    // Archived Carriers are never offered: the seeded Sprint is archived.
    await expect(carrierPicker.getByRole("option", { name: "Sprint" })).toHaveCount(0);
    await carrierPicker.selectOption({ label: "Verizon" });
    await submitDialog.getByLabel("Flavor").selectOption({ label: "Prepaid" });
    await submitDialog.getByRole("button", { name: "Submit Request" }).click();
    await expect(page.getByText("Request submitted")).toBeVisible();
    await page.getByRole("button", { name: "Close" }).click();

    const row = await approveAndBeginAgentCompletion(page, clientName, "Provision SIM");

    const number = `+1-555-${RUN_ID}`;
    await row.getByLabel(/Fee amount/).fill("15.00");
    // provision-request-details ticket AC: completing asks only for the SIM number — no Carrier
    // or Flavor picker here anymore, both already came from submission.
    await expect(row.getByRole("combobox", { name: "Carrier" })).toHaveCount(0);
    await row.getByLabel("New SIM number").fill(number);
    await completeAndGoToFleet(page, row, clientName);

    await expect(page.getByRole("row", { name: new RegExp(number.replace(/\+/g, "\\+")) })).toContainText(
      "Verizon",
    );
  });

  // provision-request-details ticket: "a Tester submits a Provision SIM with Carrier, Plan and
  // target Smartphone; the Agent completes it with a number; the Fleet shows it installed with
  // the Plan's fee" — postpaid-sim-plan ticket: the monthly fee is the Plan's price, never typed.
  test("a tester names a carrier, plan and target smartphone; completing installs the sim with the plan's fee", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Lumen Health Labs ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const targetSerial = `SN-TARGET-${RUN_ID}`;
    await addSmartphone(page, contractId, "Pixel 8", targetSerial);
    const testerEmail = `noor.hassan+${RUN_ID}@lumenhealth.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await page.getByRole("button", { name: "Submit Request" }).first().click();
    const submitDialog = page.getByRole("dialog");
    await submitDialog.getByLabel("Request type").selectOption({ label: "Provision SIM" });
    await submitDialog.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await submitDialog.getByLabel("Flavor").selectOption({ label: "Postpaid" });
    const plan = submitDialog.getByRole("combobox", { name: "Postpaid plan" });
    // Archived Plans are never offered: the seeded "Start Unlimited" is archived.
    await expect(plan.getByRole("option", { name: "Start Unlimited" })).toHaveCount(0);
    await plan.selectOption({ label: "Unlimited Welcome" });
    // The Plan sets the fee, read-only, at submission — before the SIM Card even exists.
    await expect(submitDialog).toContainText("$65.00");
    await submitDialog.getByLabel("Target Smartphone").selectOption({ label: `Pixel 8 — ${targetSerial}` });
    await submitDialog.getByRole("button", { name: "Submit Request" }).click();
    await expect(page.getByText("Request submitted")).toBeVisible();
    await page.getByRole("button", { name: "Close" }).click();

    const row = await approveAndBeginAgentCompletion(page, clientName, "Provision SIM");

    const number = `+1-555-pp-${RUN_ID}`;
    await row.getByLabel(/Fee amount/).fill("15.00");
    // provision-request-details ticket AC: completing asks only for the SIM number.
    await expect(row.getByRole("combobox", { name: "Carrier" })).toHaveCount(0);
    await row.getByLabel("New SIM number").fill(number);
    await expect(row).toContainText("Postpaid");
    await expect(row).toContainText("Unlimited Welcome");
    await expect(row).toContainText("Pixel 8");

    const targetSmartphoneId = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/smartphones`);
      const body = (await response.json()) as { id: string; model: string }[];
      return body.find((phone) => phone.model === "Pixel 8")!.id;
    }, contractId);

    await completeAndGoToFleet(page, row, clientName);

    // Scoped to the SIM Cards table specifically: the Smartphone table's own row for the target
    // Smartphone also shows this SIM's number, in its own "SIM Cards" column, so an unscoped
    // row-name match resolves to both tables' rows.
    const simTable = simCardsTable(page);
    const simRow = simTable.getByRole("row", { name: new RegExp(number.replace(/\+/g, "\\+")) });
    await expect(simRow).toContainText("Unlimited Welcome");
    await expect(simRow).toContainText("$65.00");
    await expect(simRow.getByLabel("Installed in")).toHaveValue(targetSmartphoneId);
  });

  test("a reboot or a like-for-like sim swap can never carry a fee", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Harbor & Finch Realty ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const serial = `SN-${RUN_ID}`;
    await addSmartphone(page, contractId, "Pixel 9", serial);
    const simNumber = `+1-555-${RUN_ID}`;
    await addSimCard(page, contractId, simNumber);
    const testerEmail = `charlotte.finch+${RUN_ID}@harborfinch.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Reboot", { smartphoneOptionLabel: `Pixel 9 — ${serial}` });
    await submitRequestAsTester(page, "SIM Swap", {
      smartphoneOptionLabel: `Pixel 9 — ${serial}`,
      simCardOptionLabel: `${simNumber} — Verizon`,
    });

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

  // replace-requests ticket: a Tester submits a Replace SIM; the Agent completes it; the Fleet
  // shows the old SIM Card retired and the new one in the same Smartphone (ticket's own E2E AC).
  test("a tester submits a replace sim; completing it retires the old one and installs the new one in its smartphone", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Castellane Import Co ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const serial = `SN-REPLACE-${RUN_ID}`;
    await addSmartphone(page, contractId, "Pixel 8", serial);
    const oldNumber = `+1-555-old-${RUN_ID}`;
    await addSimCard(page, contractId, oldNumber);

    // Install the old SIM Card into the Smartphone from the Fleet page, so completion has a slot
    // to hand over.
    await page.goto("/manager/contracts/" + contractId);
    const simRow = page.getByRole("row", { name: new RegExp(oldNumber.replace(/\+/g, "\\+")) });
    await simRow.getByLabel("Installed in").selectOption({ label: `Pixel 8 — ${serial}` });
    await expect(simRow.getByLabel("Installed in")).not.toHaveValue("");

    const testerEmail = `daniela.ruiz+${RUN_ID}@castellane.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Replace SIM", { simCardOptionLabel: `${oldNumber} — Verizon` });

    const row = await approveAndBeginAgentCompletion(page, clientName, "Replace SIM");

    const newNumber = `+1-555-new-${RUN_ID}`;
    await row.getByLabel(/Fee amount/).fill("18.00");
    await row.getByLabel("New SIM number").fill(newNumber);
    await row.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await row.getByLabel("Flavor").selectOption("PREPAID");
    await completeAndGoToFleet(page, row, clientName);

    const simTable = simCardsTable(page);
    await expect(simTable.getByRole("row", { name: new RegExp(oldNumber.replace(/\+/g, "\\+")) })).toContainText(
      "Retired",
    );
    const newRow = simTable.getByRole("row", { name: new RegExp(newNumber.replace(/\+/g, "\\+")) });
    await expect(newRow).toContainText("Active");
    await expect(newRow.getByLabel("Installed in")).toHaveValue(
      await page.evaluate(async (contractId) => {
        const response = await fetch(`/api/contracts/${contractId}/smartphones`);
        const body = (await response.json()) as { id: string; model: string }[];
        return body.find((phone) => phone.model === "Pixel 8")!.id;
      }, contractId),
    );
  });
});
