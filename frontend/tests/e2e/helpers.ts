import { expect, type Locator, type Page } from "@playwright/test";

/**
 * Shared login and fixture setup for the e2e suite's real-backend specs (driven against a real
 * backend + Postgres, at the accessibility-tree level per spec.md's Testing decisions). Extracted
 * from the near-identical copies that had accumulated across topup-fee-from-option.spec.ts,
 * fee-logging-and-provisioning.spec.ts, client-invoice-generation.spec.ts,
 * client-invoice-submission-and-visibility.spec.ts, fleet-management.spec.ts and
 * carrier-catalog.spec.ts — no behaviour change, same steps and assertions each caller had inline.
 */
export const SEEDED_USERS = {
  manager: { username: "manager@example.com", password: "ChangeMe123!" },
  agent: { username: "agent@example.com", password: "AgentDemo123!" },
} as const;

/** The seeded agent@example.com login resolves to this Agent (V5 migration), USD. */
export const SEEDED_AGENT_LABEL = "Jordan Ellis · USD";

export async function login(page: Page, username: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  // Wait for the post-login redirect before the caller navigates anywhere else — otherwise a
  // page.goto() right after this can race and cancel the in-flight session-cookie exchange,
  // leaving no session and every following action silently bounced back to /login.
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

export async function logout(page: Page) {
  // viewer-chip-menu ticket: "Log out" moved from a standalone top-bar button into the viewer
  // chip's menu, opened first here — the item keeps its shipped accessible name, so this is the
  // only step every caller needed added.
  //
  // A dedicated test id (review finding F9), not `[aria-haspopup="menu"]`: that attribute
  // selector is unambiguous today only because `ContractSwitcher` is `aria-haspopup="listbox"`,
  // and the trigger's own accessible name is the signed-in viewer's own label (varies per user
  // and per console), so neither survives a second menu ever landing in the shared shell.
  await page.getByTestId("viewer-menu-trigger").click();
  await page.getByRole("menuitem", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login/);
}

/** Creates a fresh Client and returns its id. */
async function createClient(page: Page, clientName: string): Promise<string> {
  await page.goto("/manager/clients");
  await page.getByRole("button", { name: "Add client" }).first().click();
  await page.getByLabel("Client name").fill(clientName);
  await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("cell", { name: clientName })).toBeVisible();
  const clientHref = await page.getByRole("link", { name: clientName }).getAttribute("href");
  return clientHref!.split("/").pop()!;
}

/** Creates a Contract linking an existing Client (by name) to the seeded Agent. */
async function createContractForClient(page: Page, clientName: string): Promise<void> {
  await page.goto("/manager/contracts");
  await page.getByRole("button", { name: "Add contract" }).first().click();
  await page.getByLabel("Client").selectOption({ label: clientName });
  await page.getByLabel("Agent").selectOption({ label: SEEDED_AGENT_LABEL });
  await page.getByRole("dialog").getByRole("button", { name: "Add contract" }).click();
  await expect(page.getByRole("row", { name: new RegExp(clientName) })).toBeVisible();
}

/** Creates a fresh Client, then a Contract linking it to the seeded Agent. Returns both ids. */
export async function createClientAndContractWithSeededAgent(
  page: Page,
  clientName: string,
): Promise<{ clientId: string; contractId: string }> {
  const clientId = await createClient(page, clientName);
  await createContractForClient(page, clientName);

  await page.getByRole("link", { name: clientName }).click();
  await expect(page).toHaveURL(/\/manager\/contracts\/.+/);
  const contractId = page.url().split("/").pop()!;

  return { clientId, contractId };
}

/** Adds a Tester under an existing Client (from its manager detail page) and returns their email. */
export async function addTester(page: Page, clientId: string, email: string, password: string) {
  await page.goto(`/manager/clients/${clientId}`);
  await page.getByRole("button", { name: "Add tester" }).first().click();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Temporary password").fill(password);
  await page.getByRole("dialog").getByRole("button", { name: "Add tester" }).click();
  await expect(page.getByRole("cell", { name: email })).toBeVisible();
}

/** Creates a fresh Client with one Tester, and a Contract linking it to the seeded Agent. Returns both ids. */
export async function createContractWithTester(
  page: Page,
  clientName: string,
  testerEmail: string,
): Promise<{ clientId: string; contractId: string }> {
  const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
  await addTester(page, clientId, testerEmail, "Passw0rd!23");
  return { clientId, contractId };
}

/**
 * Adds a Smartphone to a Contract's Fleet from the Manager's Contract detail page. Returns its
 * serial. `owner` (return-client-owned-smartphones/manager-decides-return-disposition tickets)
 * selects the Owner field explicitly only when given — omitted, the dialog's own default (Company)
 * applies, matching every caller that predates the Owner field.
 */
export async function addSmartphone(
  page: Page,
  contractId: string,
  model: string,
  serial: string,
  owner?: "Client" | "Company",
) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add smartphone" }).first().click();
  await page.getByLabel("Model").fill(model);
  await page.getByLabel("Serial").fill(serial);
  if (owner) {
    await page.getByLabel("Owner").selectOption({ label: owner });
  }
  await page.getByRole("dialog").getByRole("button", { name: "Add smartphone" }).click();
  await expect(page.getByRole("cell", { name: serial })).toBeVisible();
}

/**
 * Adds a SIM card to a Contract's Fleet from the Manager's Contract detail page, on a given
 * Carrier and Flavor (defaulting to Verizon/Prepaid — the seeded active US Carrier with active
 * Topup Options, per the reboot-and-topup-details ticket's fixture needs). `planLabel`
 * (cancelled-sim-billed-through-its-month ticket) picks an existing Postpaid Plan by name — e.g.
 * the seeded Verizon "Unlimited Welcome" Plan — instead of `addPostpaidSimCard`'s own "create a
 * fresh Plan at a chosen price" fixture shape.
 */
export async function addSimCard(
  page: Page,
  contractId: string,
  number: string,
  carrierLabel = "Verizon",
  flavor: "PREPAID" | "POSTPAID" = "PREPAID",
  planLabel?: string,
) {
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add SIM card" }).first().click();
  await page.getByLabel("Number").fill(number);
  await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: carrierLabel });
  await page.getByLabel("Flavor").selectOption(flavor);
  if (planLabel) {
    await page.getByRole("combobox", { name: "Postpaid plan" }).selectOption({ label: planLabel });
  }
  await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
  await expect(page.getByRole("cell", { name: number })).toBeVisible();
}

/** Scopes a Pending Requests page row by both type and Client name — the page is tenant-wide and
 * can also show an unrelated seeded Request, so every caller's `clientName` (already unique per
 * test run) disambiguates it. */
export function pendingRequestRow(page: Page, clientName: string, typeLabel: string) {
  return page.getByRole("row", { name: new RegExp(`${typeLabel}.*${clientName}`) });
}

/**
 * Approves a Pending Approval Request from the Manager's Pending Requests page
 * (manager-approves-requests ticket) — every Provision/Replace fixture needs this step before the
 * Agent can progress it. `unitOverrides` (manager-decides-return-disposition/agent-stock tickets)
 * maps a substring/regex of a Return's own unit label (its model+serial, or SIM number) to a
 * Disposition label to pick instead of the row's own preselected one — e.g. "Kept in Stock" in
 * place of the preselected "Posted to company" — before approving; omitted, every unit approves
 * with its own preselected choice, the shape every non-Return caller already relies on.
 */
export async function approveFromPendingRequests(
  page: Page,
  clientName: string,
  typeLabel: string,
  unitOverrides: Record<string, string> = {},
) {
  await page.goto("/manager/requests");
  const row = pendingRequestRow(page, clientName, typeLabel);
  for (const [unitLabelPattern, dispositionLabel] of Object.entries(unitOverrides)) {
    await row.getByLabel(new RegExp(unitLabelPattern)).selectOption({ label: dispositionLabel });
  }
  await Promise.all([
    page.waitForResponse((resp) => /\/api\/requests\/.+\/approve$/.test(resp.url())),
    row.getByRole("button", { name: "Approve" }).click(),
  ]);
  await expect(page.getByRole("row", { name: new RegExp(clientName) })).not.toBeVisible();
}

/**
 * Completes a Return Request from the Agent's own Requests row (return-client-owned-smartphones/
 * manager-decides-return-disposition/agent-stock tickets). `cancellationDates`, keyed the same way
 * `approveFromPendingRequests`'s own `unitOverrides` is (a substring/regex of the Cancelled SIM
 * Card's own number), fills that unit's own effective-date field before the completing form's
 * submit click — a Return with no Cancelled unit needs no dates and completes with the generic
 * single-click path every other no-input type already uses. Waits for the actual completion
 * PATCH's response rather than the DOM settling once a form is involved: the completing form's own
 * submit button shares the literal "Mark Completed" label with the trigger button that opened it,
 * so a bare `toContainText` assertion can pass on that still-open button before the request
 * round-trips (fulfil-from-stock ticket's own implementer note on this exact race).
 */
export async function completeReturnRequest(page: Page, row: Locator, cancellationDates: Record<string, string> = {}) {
  await row.getByRole("button", { name: "Mark Completed" }).click();
  if (Object.keys(cancellationDates).length === 0) {
    await expect(row).toContainText("Completed");
    return;
  }
  for (const [unitLabelPattern, date] of Object.entries(cancellationDates)) {
    const dateField = row.getByLabel(new RegExp(unitLabelPattern));
    await expect(dateField).toHaveAttribute("type", "date");
    await dateField.fill(date);
  }
  await Promise.all([
    page.waitForResponse((resp) => /\/requests\/.+\/status$/.test(resp.url()) && resp.request().method() === "PATCH"),
    row.getByRole("button", { name: "Mark Completed" }).click(),
  ]);
  await expect(row.getByText("Completed", { exact: true })).toBeVisible();
}

/**
 * Adds a Smartphone and a SIM card (Verizon Prepaid, so Topup has an active Option to offer) to a
 * Contract's Fleet, and returns the exact option labels the Submit-Request dialog's
 * Smartphone/SIM Card pickers render — the Fleet fixture Reboot, Topup and SIM Swap Requests all
 * need at submission (reboot-and-topup-details/sim-swap-moves tickets).
 */
export async function addFleetForRebootAndTopup(
  page: Page,
  contractId: string,
  suffix: string,
): Promise<{ smartphoneOptionLabel: string; simCardOptionLabel: string }> {
  const serial = `SN-${suffix}`;
  await addSmartphone(page, contractId, "Pixel 9", serial);
  const number = `+1-555-${suffix}`;
  await addSimCard(page, contractId, number);
  return { smartphoneOptionLabel: `Pixel 9 — ${serial}`, simCardOptionLabel: `${number} — Verizon` };
}

/** Scopes to the SIM Cards table specifically — a Smartphone's own row also shows an installed
 * SIM's number, in its own "SIM Cards" column (sim-installed-in-smartphone ticket). */
export function simCardsTable(page: Page) {
  return page.getByRole("heading", { name: "SIM Cards" }).locator("xpath=following::table[1]");
}

/** Sets a SIM Card's Installed-in Smartphone from the Manager's Contract Fleet view. */
export async function installSimCard(page: Page, contractId: string, number: string, smartphoneOptionLabel: string) {
  await page.goto(`/manager/contracts/${contractId}`);
  const row = simCardsTable(page).getByRole("row", { name: new RegExp(number.replace(/\+/g, "\\+")) });
  await row.getByLabel("Installed in").selectOption({ label: smartphoneOptionLabel });
  await expect(row.getByLabel("Installed in")).not.toHaveValue("");
}

/**
 * Creates a Contract the seeded Agent (Jordan Ellis) does NOT hold: a fresh Client and a fresh
 * Agent of its own. Shared by every "an agent/tester on a different contract is rejected" 403
 * boundary test, which only needs a Contract that isn't the seeded Agent's — the Client and Agent
 * names themselves are never asserted on, beyond needing to be unique within their own spec file
 * (a caller whose file already uses the default name for something else passes its own).
 */
export async function createUnrelatedContract(
  page: Page,
  suffix: string,
  clientName = `Bright Path Clinics ${suffix}`,
): Promise<{ clientId: string; contractId: string }> {
  const otherClientName = clientName;
  await page.goto("/manager/clients");
  await page.getByRole("button", { name: "Add client" }).first().click();
  await page.getByLabel("Client name").fill(otherClientName);
  await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("cell", { name: otherClientName })).toBeVisible();
  const clientHref = await page.getByRole("link", { name: otherClientName }).getAttribute("href");
  const clientId = clientHref!.split("/").pop()!;

  const otherAgentName = `Priya Nair ${suffix}`;
  await page.goto("/manager/agents");
  await page.getByRole("button", { name: "Add agent" }).first().click();
  await page.getByLabel("Agent name").fill(otherAgentName);
  await page.getByLabel("Country").selectOption("PHILIPPINES");
  await page.getByLabel("Standing monthly salary").fill("1500");
  await page.getByLabel("Email").fill(`priya.nair+${suffix}@agents.example`);
  await page.getByLabel("Temporary password").fill("Passw0rd!23");
  await page.getByRole("dialog").getByRole("button", { name: "Add agent" }).click();
  await expect(page.getByRole("row", { name: new RegExp(otherAgentName) })).toBeVisible();

  await page.goto("/manager/contracts");
  await page.getByRole("button", { name: "Add contract" }).first().click();
  await page.getByLabel("Client").selectOption({ label: otherClientName });
  await page.getByLabel("Agent").selectOption({ label: `${otherAgentName} · PHP` });
  await page.getByRole("dialog").getByRole("button", { name: "Add contract" }).click();
  await page.getByRole("link", { name: otherClientName }).click();
  await expect(page).toHaveURL(/\/manager\/contracts\/.+/);
  const contractId = page.url().split("/").pop()!;

  return { clientId, contractId };
}

/**
 * Adds a Postpaid Plan at a given price to the seeded Verizon Carrier, as the Manager, and returns
 * its name. A Postpaid SIM's monthly fee is copied from its Plan (postpaid-sim-plan ticket), so a
 * test that wants a specific fee puts that price in the catalog first.
 */
export async function addPostpaidPlanToVerizon(page: Page, planName: string, monthlyPrice: string) {
  await page.goto("/manager/carriers?country=UNITED_STATES");
  const verizon = page
    .getByRole("list", { name: "Carriers" })
    .getByRole("listitem")
    .filter({ has: page.getByText("Verizon", { exact: true }) })
    .first();
  await verizon.getByRole("button", { name: "Add a postpaid plan to Verizon" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Name").fill(planName);
  await dialog.getByLabel("Monthly price (USD)").fill(monthlyPrice);
  await dialog.getByRole("button", { name: "Add postpaid plan" }).click();
  await expect(dialog).toBeHidden();
  return planName;
}

/** Adds a Postpaid SIM card to a Contract's Fleet from the Manager's Contract detail page. */
export async function addPostpaidSimCard(page: Page, contractId: string, number: string, monthlyFee: string) {
  const planName = await addPostpaidPlanToVerizon(
    page,
    // Never derived from the SIM number: the Fleet table shows both, and a Plan name containing
    // the number would make a row locator for the number ambiguous.
    `Test plan ${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`,
    monthlyFee,
  );
  await page.goto(`/manager/contracts/${contractId}`);
  await page.getByRole("button", { name: "Add SIM card" }).first().click();
  await page.getByLabel("Number").fill(number);
  await page.getByRole("combobox", { name: "Carrier" }).selectOption({ label: "Verizon" });
  await page.getByLabel("Flavor").selectOption({ label: "Postpaid" });
  await page.getByRole("combobox", { name: "Postpaid plan" }).selectOption({ label: planName });
  await page.getByRole("dialog").getByRole("button", { name: "Add SIM card" }).click();
  await expect(page.getByRole("cell", { name: number })).toBeVisible();
}

/**
 * Adds a Tester, submits a Request of the given type as them, then logs back in as the seeded
 * Agent and opens the Contract's Requests page with it already selected in the switcher — ready
 * for the caller to act on the new Request's row. Shared by tests that set up a Contract, then
 * need an Agent-side Request row to act on. `fleet` is forwarded to `submitRequestAsTester` for a
 * type whose per-type details need a Fleet fixture named (Reboot/Topup/SIM Swap/Provision/Replace).
 */
export async function addTesterAndSubmitRequestAsAgent(
  page: Page,
  clientId: string,
  clientName: string,
  testerEmail: string,
  requestTypeLabel: string,
  fleet?: SubmitRequestFleetOptions,
) {
  await addTester(page, clientId, testerEmail, "Passw0rd!23");

  await logout(page);
  await login(page, testerEmail, "Passw0rd!23");
  await submitRequestAsTester(page, requestTypeLabel, fleet);

  await logout(page);
  await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
  await page.goto("/agent/requests");
  await selectContractInSwitcher(page, clientName);
}

/** Selects the Contract matching `clientName` in a Contract switcher, if more than one exists. */
export async function selectContractInSwitcher(page: Page, clientName: string) {
  const trigger = page.locator('[aria-haspopup="listbox"]');
  if ((await trigger.count()) > 0) {
    await trigger.click();
    await page.getByRole("option", { name: new RegExp(clientName) }).click();
  }
}

/**
 * Every per-type Request-details field a Tester's Submit-Request dialog can need, keyed loosely
 * enough for `submitRequestAsTester` to pick the right ones for the type it's given
 * (provision-request-details/replace-requests/sim-swap-moves tickets: each type owns its own
 * details section within the one dialog).
 */
export interface SubmitRequestFleetOptions {
  smartphoneOptionLabel?: string;
  simCardOptionLabel?: string;
  requestedModel?: string;
  carrierLabel?: string;
  flavorLabel?: "Prepaid" | "Postpaid";
  planLabel?: string;
  targetSmartphoneOptionLabel?: string;
  topupOptionLabel?: string;
  /** other-replaces-repair ticket: an Other Request requires a description. */
  description?: string;
  /** return-client-owned-smartphones ticket: the option label(s) to pick in the Return's own
   * multi-select — a Smartphone's `"<model> — <serial>"`, or a SIM Card's own number. */
  returnedUnitLabels?: string[];
}

/** Submits a Request of the given type as the currently-logged-in Tester. */
export async function submitRequestAsTester(page: Page, requestTypeLabel: string, fleet?: SubmitRequestFleetOptions) {
  await page.goto("/client/requests");
  await page.getByRole("button", { name: "Submit Request" }).first().click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Request type").selectOption({ label: requestTypeLabel });
  if (requestTypeLabel === "Other" && fleet?.description) {
    await dialog.getByLabel("Description").fill(fleet.description);
  }
  if (requestTypeLabel === "Reboot") {
    await dialog.getByLabel("Smartphone to reboot").selectOption({ label: fleet!.smartphoneOptionLabel! });
  }
  if (requestTypeLabel === "Topup") {
    await dialog.getByLabel("SIM Card to top up").selectOption({ label: fleet!.simCardOptionLabel! });
    await dialog.getByLabel("Topup Option").selectOption({ label: fleet?.topupOptionLabel ?? "Prepaid Refill 35" });
  }
  if (requestTypeLabel === "SIM Swap") {
    // sim-swap-moves ticket: a plain move names the SIM Card and its destination Smartphone.
    await dialog.getByLabel("SIM Card to move").selectOption({ label: fleet!.simCardOptionLabel! });
    await dialog.getByLabel("Destination Smartphone").selectOption({ label: fleet!.smartphoneOptionLabel! });
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
  if (requestTypeLabel === "Return") {
    // return-client-owned-smartphones ticket: one multi-select (role listbox), not two pickers.
    await dialog
      .getByRole("listbox", { name: "Units to return" })
      .selectOption(fleet!.returnedUnitLabels!.map((label) => ({ label })));
  }
  await dialog.getByRole("button", { name: "Submit Request" }).click();
  await expect(page.getByText("Request submitted")).toBeVisible();
  await page.getByRole("button", { name: "Close" }).click();
}

/**
 * Fetches a Contract's Smartphones/SIM Cards from the browser — used for API-level post-completion
 * Fleet-state assertions (return-client-owned-smartphones/manager-decides-return-disposition/
 * agent-stock tickets' own precedent: a Smartphone's Fleet row can also show an installed SIM's
 * number, so a text-content row assertion is the wrong tool for "is this unit Retired/Active").
 * The returned shape is a superset of every caller's own field needs; an unused field is simply
 * never read.
 */
export async function fetchSmartphones(page: Page, contractId: string) {
  return page.evaluate(async (contractId) => {
    const response = await fetch(`/api/contracts/${contractId}/smartphones`);
    return (await response.json()) as { id: string; serial: string; status: string }[];
  }, contractId);
}

export async function fetchSimCards(page: Page, contractId: string) {
  return page.evaluate(async (contractId) => {
    const response = await fetch(`/api/contracts/${contractId}/sim-cards`);
    return (await response.json()) as {
      id: string;
      number: string;
      status: string;
      installedInSmartphoneId?: string;
      cancellationEffectiveDate?: string;
    }[];
  }, contractId);
}

/** Fetches a Contract's Fees from the browser — there's no dedicated Fees list view yet. */
export async function fetchFees(page: Page, contractId: string) {
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
