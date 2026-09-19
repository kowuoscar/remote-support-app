import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addFleetForRebootAndTopup,
  addTester,
  createClientAndContractWithSeededAgent,
  createUnrelatedContract,
  login,
  logout,
  selectContractInSwitcher,
  submitRequestAsTester,
} from "./helpers";

/**
 * Request submission and visibility (tester-request-submission ticket), driven against a real
 * backend + Postgres (see playwright.e2e.config.ts), at the accessibility-tree level per
 * spec.md's Testing decisions. Mirrors tests/e2e/fleet-management.spec.ts's pattern. The seeded
 * agent@example.com login resolves to the "Jordan Ellis" Agent (V5 migration).
 */

const OTHER_DESCRIPTION = "Screen protector needs replacing";

// manager-approves-requests ticket: Provision Smartphone and Provision SIM now start Pending
// Approval, whoever raises them — every other type in this loop still starts Submitted.
const APPROVAL_REQUIRED_LABELS = new Set(["Provision Smartphone", "Provision SIM"]);

/** Submits every request type in `REQUEST_TYPE_LABELS`, filling in each type's own details. */
async function submitEveryRequestType(
  page: import("@playwright/test").Page,
  fleet: { smartphoneOptionLabel: string; simCardOptionLabel: string },
) {
  for (const typeLabel of REQUEST_TYPE_LABELS) {
    await submitRequestAsTester(page, typeLabel, {
      smartphoneOptionLabel: fleet.smartphoneOptionLabel,
      simCardOptionLabel: fleet.simCardOptionLabel,
      carrierLabel: "Verizon",
      flavorLabel: "Prepaid",
      // Other requires a description; every other type leaves it blank, exactly as before.
      description: OTHER_DESCRIPTION,
    });
  }
}

// Date.now() alone can collide across spec files: Playwright's collection phase can
// import several spec files within the same millisecond, and more than one file in this
// suite picks the same literal client name (e.g. "Aurora Retail Group") for its first
// test, so an exact RUN_ID match produces a real duplicate row, not just a slow test.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
const REQUEST_TYPE_LABELS = ["Reboot", "Topup", "SIM Swap", "Provision Smartphone", "Provision SIM", "Other"];

test.describe("tester request submission", () => {
  test("a tester submits every request type, another tester at the same client sees them, and the agent sees them filtered by contract", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Aurora Retail Group ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const fleet = await addFleetForRebootAndTopup(page, contractId, RUN_ID);

    const firstTesterEmail = `priya.raman+${RUN_ID}@aurora.example`;
    await addTester(page, clientId, firstTesterEmail, "Passw0rd!23");
    const secondTesterEmail = `owen.reyes+${RUN_ID}@aurora.example`;
    await addTester(page, clientId, secondTesterEmail, "Passw0rd!23");

    await logout(page);
    await login(page, firstTesterEmail, "Passw0rd!23");
    await expect(page).toHaveURL(/\/client$/);

    await submitEveryRequestType(page, fleet);
    await page.goto("/client/requests");
    for (const typeLabel of REQUEST_TYPE_LABELS) {
      await expect(page.getByRole("row", { name: new RegExp(typeLabel) })).toBeVisible();
    }

    // A different Tester at the same Client sees every Request raised by anyone at that Client,
    // not just their own.
    await logout(page);
    await login(page, secondTesterEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    for (const typeLabel of REQUEST_TYPE_LABELS) {
      await expect(page.getByRole("row", { name: new RegExp(typeLabel) })).toBeVisible();
      await expect(page.getByRole("row", { name: new RegExp(typeLabel) })).toContainText(firstTesterEmail);
    }

    // The Agent sees the same Requests queued for their Contract, including the Other Request's
    // description (other-replaces-repair ticket AC: "a Tester submits an Other Request with a
    // description and the Agent sees it").
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);
    for (const typeLabel of REQUEST_TYPE_LABELS) {
      await expect(page.getByRole("row", { name: new RegExp(typeLabel) })).toBeVisible();
      await expect(page.getByRole("row", { name: new RegExp(typeLabel) })).toContainText(
        APPROVAL_REQUIRED_LABELS.has(typeLabel) ? "Pending Approval" : "Submitted",
      );
    }
    await expect(page.getByRole("row", { name: /Other/ })).toContainText(OTHER_DESCRIPTION);
  });

  test("a tester from a different client and an agent on a different contract are both rejected", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const { contractId: otherContractId } = await createUnrelatedContract(page, RUN_ID);

    // A Contract a fresh Tester's Client does NOT hold — reuse the same "other" Contract above.

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    const agentStatus = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`);
      return response.status;
    }, otherContractId);
    expect(agentStatus).toBe(403);

    // A fresh Tester at an unrelated Client cannot submit a Request against that Contract, nor see it.
    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const testerClientName = `Solene Cosmetics ${RUN_ID}`;
    await page.goto("/manager/clients");
    await page.getByRole("button", { name: "Add client" }).first().click();
    await page.getByLabel("Client name").fill(testerClientName);
    await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
    await page.getByRole("link", { name: testerClientName }).click();
    await expect(page).toHaveURL(/\/manager\/clients\/.+/);
    const testerClientId = page.url().split("/").pop()!;
    const testerEmail = `elise.fabron+${RUN_ID}@solene.example`;
    await addTester(page, testerClientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    const testerGetStatus = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`);
      return response.status;
    }, otherContractId);
    expect(testerGetStatus).toBe(403);

    const testerPostStatus = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type: "REBOOT" }),
      });
      return response.status;
    }, otherContractId);
    expect(testerPostStatus).toBe(403);
  });
});
