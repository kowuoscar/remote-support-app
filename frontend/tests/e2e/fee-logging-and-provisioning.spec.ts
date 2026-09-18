import { test, expect, type Page } from "@playwright/test";

/**
 * Fee logging, its traceability rule, and the provisioning side-effect on Fleet
 * (fee-logging-and-provisioning ticket), driven against a real backend + Postgres (see
 * playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing decisions.
 * Mirrors tests/e2e/agent-request-fulfillment.spec.ts's pattern. The seeded agent@example.com
 * login resolves to the "Jordan Ellis" Agent (V5 migration), USD.
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

/** Adds a Smartphone to a Contract's Fleet from the Manager's Contract detail page. Returns its serial. */
async function addSmartphone(page: Page, contractId: string, model: string, serial: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add smartphone" }).first().click();
  await page.getByLabel("Model").fill(model);
  await page.getByLabel("Serial").fill(serial);
  await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
  await expect(page.getByRole("cell", { name: serial })).toBeVisible();
}

/**
 * reboot-and-topup-details ticket: a Topup Request now names a SIM Card from the Contract's
 * Fleet. Verizon (the seeded active US Carrier) has active Topup Options (V22 migration).
 */
async function addSimCard(page: Page, contractId: string, number: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add SIM card" }).first().click();
  await page.getByLabel("Number").fill(number);
  await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
  await page.getByLabel("Flavor").selectOption("PREPAID");
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

async function submitRequestAsTester(
  page: Page,
  requestTypeLabel: string,
  fleet?: {
    smartphoneOptionLabel?: string;
    simCardOptionLabel?: string;
    // provision-request-details ticket: Provision Smartphone/SIM now need their own details at
    // submission, not just at completion.
    requestedModel?: string;
    carrierLabel?: string;
    flavorLabel?: "Prepaid" | "Postpaid";
    planLabel?: string;
    targetSmartphoneOptionLabel?: string;
  },
) {
  await page.goto("/client/requests");
  await page.getByRole("button", { name: "Submit Request" }).first().click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Request type").selectOption({ label: requestTypeLabel });
  if (requestTypeLabel === "Reboot") {
    await dialog.getByLabel("Smartphone to reboot").selectOption({ label: fleet!.smartphoneOptionLabel! });
  }
  if (requestTypeLabel === "Topup") {
    await dialog.getByLabel("SIM Card to top up").selectOption({ label: fleet!.simCardOptionLabel! });
    await dialog.getByLabel("Topup Option").selectOption({ label: "Prepaid Refill 35" });
  }
  if (requestTypeLabel === "Provision Smartphone") {
    await dialog.getByLabel("Requested model").fill(fleet!.requestedModel!);
  }
  if (requestTypeLabel === "Provision SIM") {
    await dialog.getByRole("combobox", { name: "Carrier" }).selectOption({ label: fleet!.carrierLabel! });
    await dialog.getByLabel("Flavor").selectOption({ label: fleet!.flavorLabel ?? "Prepaid" });
    if (fleet!.flavorLabel === "Postpaid" && fleet!.planLabel) {
      await dialog.getByRole("combobox", { name: "Postpaid plan" }).selectOption({ label: fleet!.planLabel });
    }
    if (fleet!.targetSmartphoneOptionLabel) {
      await dialog.getByLabel("Target Smartphone").selectOption({ label: fleet!.targetSmartphoneOptionLabel });
    }
  }
  // replace-requests ticket: a Replace SIM Request names the SIM Card to replace, the same
  // Active-units-only picker Topup's own target uses, just under its own label.
  if (requestTypeLabel === "Replace SIM") {
    await dialog.getByLabel("SIM Card to replace").selectOption({ label: fleet!.simCardOptionLabel! });
  }
  await dialog.getByRole("button", { name: "Submit Request" }).click();
  await expect(page.getByText("Request submitted")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();
}

/** Fetches a Contract's Fees from the browser — there's no dedicated Fees list view yet. */
async function fetchFees(page: Page, contractId: string) {
  return page.evaluate(async (contractId) => {
    const response = await fetch(`/api/contracts/${contractId}/fees`);
    return (await response.json()) as {
      requestId: string;
      feeType: string;
      amount: number;
      topupOptionId?: string;
    }[];
  }, contractId);
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

  // reboot-and-topup-details ticket: completing a Topup Request pre-fills the Fee amount from
  // its own Topup Option and links the Fee to it (still editable, just not overridden here).
  test("completing a topup request pre-fills the fee from its own topup option and links it", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Kessler & Vance LLP ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const simNumber = `+1-555-opt-${RUN_ID}`;
    await addSimCard(page, contractId, simNumber);
    const testerEmail = `helena.voss+${RUN_ID}@kessler.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await page.getByRole("button", { name: "Submit Request" }).first().click();
    const submitDialog = page.getByRole("dialog");
    await submitDialog.getByLabel("Request type").selectOption({ label: "Topup" });
    await submitDialog.getByLabel("SIM Card to top up").selectOption({ label: `${simNumber} — Verizon` });
    // Verizon's seeded "Prepaid Refill 35" Option, $35.00 (V22 migration).
    await submitDialog.getByLabel("Topup Option").selectOption({ label: "Prepaid Refill 35" });
    await submitDialog.getByRole("button", { name: "Submit Request" }).click();
    await expect(page.getByText("Request submitted")).toBeVisible();
    await page.getByRole("button", { name: "Close" }).click();

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Topup/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();

    // Pre-filled from the Request's own Topup Option — the Agent completes without changing it.
    await expect(row.getByLabel(/Fee amount/)).toHaveValue("35");

    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);
    await expect(row).toContainText("Completed");

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
  test("a tester names the requested model at submission, and completing needs no agent input", async ({
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
    await submitRequestAsTester(page, "Provision Smartphone", { requestedModel: "iPhone 15" });

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Provision Smartphone/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();
    await expect(row.getByText("Adds")).toContainText("iPhone 15");

    await row.getByLabel(/Fee amount/).fill("150.00");
    // Completing this way fires two sequential requests (status PATCH, then Fee POST) before the
    // row re-renders — wait for the Fee POST itself to resolve rather than only the row's text.
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);
    await expect(row).toContainText("Completed");

    // Regression (fleet-management): the Fleet view shows the new unit, and the older one
    // untouched — Provision no longer retires anything.
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByRole("row", { name: new RegExp(oldSerial) })).toContainText("Active");
    await expect(page.getByRole("row", { name: "iPhone 15" })).toContainText("Active");
  });

  test("a tester names the carrier at submission, and the agent completes with only a number", async ({
    page,
  }) => {
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

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Provision SIM/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();

    const number = `+1-555-${RUN_ID}`;
    await row.getByLabel(/Fee amount/).fill("15.00");
    // provision-request-details ticket AC: completing asks only for the SIM number — no Carrier
    // or Flavor picker here anymore, both already came from submission.
    await expect(row.getByRole("combobox", { name: "Carrier" })).toHaveCount(0);
    await row.getByLabel("New SIM number").fill(number);
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);
    await expect(row).toContainText("Completed");

    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);
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

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Provision SIM/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();

    const number = `+1-555-pp-${RUN_ID}`;
    await row.getByLabel(/Fee amount/).fill("15.00");
    // provision-request-details ticket AC: completing asks only for the SIM number.
    await expect(row.getByRole("combobox", { name: "Carrier" })).toHaveCount(0);
    await row.getByLabel("New SIM number").fill(number);
    await expect(row).toContainText("Postpaid");
    await expect(row).toContainText("Unlimited Welcome");
    await expect(row).toContainText("Pixel 8");

    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);
    await expect(row).toContainText("Completed");

    const targetSmartphoneId = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/smartphones`);
      const body = (await response.json()) as { id: string; model: string }[];
      return body.find((phone) => phone.model === "Pixel 8")!.id;
    }, contractId);

    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);
    // Scoped to the SIM Cards table specifically: the Smartphone table's own row for the target
    // Smartphone also shows this SIM's number, in its own "SIM Cards" column, so an unscoped
    // row-name match resolves to both tables' rows.
    const simTable = page.getByRole("heading", { name: "SIM Cards" }).locator("xpath=following::table[1]");
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
    const testerEmail = `charlotte.finch+${RUN_ID}@harborfinch.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Reboot", { smartphoneOptionLabel: `Pixel 9 — ${serial}` });
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
    const rebootStatus = await page.evaluate(
      async ({ contractId, requestId }) => {
        const response = await fetch(`/api/contracts/${contractId}/fees`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ requestId, feeType: "TOPUP", amount: 10 }),
        });
        return response.status;
      },
      { contractId, requestId: requestIds.reboot },
    );
    expect(rebootStatus).toBe(400);

    const simSwapStatus = await page.evaluate(
      async ({ contractId, requestId }) => {
        const response = await fetch(`/api/contracts/${contractId}/fees`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ requestId, feeType: "PROVISION_SIM", amount: 15 }),
        });
        return response.status;
      },
      { contractId, requestId: requestIds.simSwap },
    );
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

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Replace SIM/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();

    const newNumber = `+1-555-new-${RUN_ID}`;
    await row.getByLabel(/Fee amount/).fill("18.00");
    await row.getByLabel("New SIM number").fill(newNumber);
    await row.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
    await row.getByLabel("Flavor").selectOption("PREPAID");
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);
    await expect(row).toContainText("Completed");

    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);
    const simTable = page.getByRole("heading", { name: "SIM Cards" }).locator("xpath=following::table[1]");
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
