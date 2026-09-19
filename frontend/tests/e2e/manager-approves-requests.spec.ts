import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addTester,
  createClientAndContractWithSeededAgent,
  login,
  logout,
  pendingRequestRow,
  selectContractInSwitcher,
  submitRequestAsTester,
} from "./helpers";

/**
 * The Company Manager's approval gate on Provision and Replace Requests
 * (request-types-and-flow spec, Lifecycle/Manager approval; manager-approves-requests ticket),
 * driven against a real backend + Postgres (see playwright.e2e.config.ts), at the
 * accessibility-tree level per spec.md's Testing decisions. Mirrors
 * tests/e2e/fee-logging-and-provisioning.spec.ts's pattern. The seeded agent@example.com login
 * resolves to the "Jordan Ellis" Agent (V5 migration), USD.
 */

// Date.now() alone can collide across spec files: Playwright's collection phase can import
// several spec files within the same millisecond, and more than one file in this suite picks the
// same literal client name (e.g. "Aurora Retail Group") for its first test, so an exact RUN_ID
// match produces a real duplicate row, not just a slow test.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("manager approves requests", () => {
  test("a tester submits a provision sim, the manager approves it from pending requests, and the agent completes it", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Aurora Retail Group ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `priya.raman+${RUN_ID}@aurora.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    // The Manager's dashboard shows the pending count, linking to the page. The dashboard
    // demonstrates the Operate-mode skeleton-loading convention, so wait for the real stats.
    await page.goto("/manager");
    await page.getByTestId("dashboard-ready").waitFor({ state: "visible" });
    const pendingRequestsStat = page.getByTestId("pending-requests-stat");
    const pendingRequestsCount = pendingRequestsStat.locator(".tnum");
    const before = Number((await pendingRequestsCount.textContent())!.trim());

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Provision SIM", { carrierLabel: "Verizon", flavorLabel: "Prepaid" });

    // The Tester sees it's Pending Approval, not yet Submitted.
    await page.goto("/client/requests");
    await expect(page.getByRole("row", { name: /Provision SIM/ })).toContainText("Pending Approval");

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);

    // The dashboard's own count went up by exactly one.
    await page.goto("/manager");
    await page.getByTestId("dashboard-ready").waitFor({ state: "visible" });
    await expect(pendingRequestsCount).toHaveText(String(before + 1));

    await page.goto("/manager/requests");
    const row = pendingRequestRow(page, clientName, "Provision SIM");
    await expect(row).toContainText(clientName);
    await expect(row).toContainText(testerEmail);
    await expect(row).toContainText("Jordan Ellis");

    await Promise.all([
      page.waitForResponse((resp) => /\/api\/requests\/.+\/approve$/.test(resp.url())),
      row.getByRole("button", { name: "Approve" }).click(),
    ]);
    await expect(page.getByRole("row", { name: new RegExp(clientName) })).not.toBeVisible();

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const agentRow = page.getByRole("row", { name: /Provision SIM/ });
    await expect(agentRow).toContainText("Submitted");
    await agentRow.getByRole("button", { name: "Mark In Progress" }).click();
    await agentRow.getByRole("button", { name: "Mark Completed" }).click();

    const number = `+1-555-${RUN_ID}`;
    // provision-request-details ticket AC: completing a Provision SIM asks only for the number —
    // no Carrier/Flavor picker (those came from submission); the Fee amount field still shows
    // since Provision SIM can carry a Fee (fee-logging-and-provisioning ticket).
    await expect(agentRow.getByRole("combobox", { name: "Carrier" })).toHaveCount(0);
    await agentRow.getByLabel(/Fee amount/).fill("15.00");
    await agentRow.getByLabel("New SIM number").fill(number);
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      agentRow.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);
    await expect(agentRow).toContainText("Completed");

    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByRole("row", { name: new RegExp(number.replace(/\+/g, "\\+")) })).toContainText(
      "Verizon",
    );
  });

  test("a manager rejects a request with a reason, and the tester sees it", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Meridian Logistics ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `owen.reyes+${RUN_ID}@meridian.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Provision Smartphone", { requestedModel: "iPhone 15 Pro" });

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/requests");
    const row = pendingRequestRow(page, clientName, "Provision Smartphone");
    await row.getByRole("button", { name: "Reject" }).click();
    const reasonText = "Budget is tight this month — resubmit next quarter";
    await row.getByLabel("Rejection reason").fill(reasonText);
    await Promise.all([
      page.waitForResponse((resp) => /\/api\/requests\/.+\/reject$/.test(resp.url())),
      row.getByRole("button", { name: "Confirm reject" }).click(),
    ]);
    await expect(page.getByRole("row", { name: new RegExp(clientName) })).not.toBeVisible();

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    const testerRow = page.getByRole("row", { name: /Provision Smartphone/ });
    await expect(testerRow).toContainText("Rejected");
    await expect(testerRow).toContainText(reasonText);
  });
});
