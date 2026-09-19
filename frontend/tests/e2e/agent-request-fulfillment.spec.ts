import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addSimCard,
  addSmartphone,
  addTester,
  createClientAndContractWithSeededAgent,
  createUnrelatedContract,
  login,
  logout,
  selectContractInSwitcher,
  submitRequestAsTester,
} from "./helpers";

/**
 * Request status lifecycle and Agent-authored/proactive logging (agent-request-fulfillment
 * ticket), driven against a real backend + Postgres (see playwright.e2e.config.ts), at the
 * accessibility-tree level per spec.md's Testing decisions. Mirrors
 * tests/e2e/tester-request-submission.spec.ts's pattern. The seeded agent@example.com login
 * resolves to the "Jordan Ellis" Agent (V5 migration).
 */

// Date.now() alone can collide across spec files: Playwright's collection phase can
// import several spec files within the same millisecond, and more than one file in this
// suite picks the same literal client name (e.g. "Aurora Retail Group") for its first
// test, so an exact RUN_ID match produces a real duplicate row, not just a slow test.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("agent request fulfillment", () => {
  test("an agent progresses a request from submitted through in progress to completed", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Aurora Retail Group ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const serial = `SN-${RUN_ID}`;
    await addSmartphone(page, contractId, "Pixel 9", serial);
    const testerEmail = `priya.raman+${RUN_ID}@aurora.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    // Reboot, not Topup: this test is about the plain status-progression mechanics, and
    // fee-logging-and-provisioning ticket makes completing a fee-eligible type (Topup included)
    // prompt for a Fee amount first — covered by its own suite (fee-logging-and-provisioning.spec.ts).
    await submitRequestAsTester(page, "Reboot", { smartphoneOptionLabel: `Pixel 9 — ${serial}` });

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Reboot/ });
    await expect(row).toContainText("Submitted");

    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");

    await row.getByRole("button", { name: "Mark Completed" }).click();
    await expect(row).toContainText("Completed");
    await expect(row.getByText("No further changes")).toBeVisible();
  });

  test("an agent cancels a request with a reason", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Meridian Logistics ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const serial = `SN-${RUN_ID}`;
    await addSmartphone(page, contractId, "Pixel 9", serial);
    const testerEmail = `owen.reyes+${RUN_ID}@meridian.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Reboot", { smartphoneOptionLabel: `Pixel 9 — ${serial}` });

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Reboot/ });
    await row.getByRole("button", { name: "Cancel" }).click();
    await row.getByLabel("Cancellation reason").fill("Tester no longer needs this");
    await row.getByRole("button", { name: "Confirm cancel" }).click();

    await expect(row).toContainText("Cancelled");
    await expect(row).toContainText("Tester no longer needs this");

    // The Tester's own list reflects the Agent's cancellation too (regression: still visible,
    // status still up to date).
    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await expect(page.getByRole("row", { name: /Reboot/ })).toContainText("Cancelled");
  });

  test("an agent logs a proactive request on a tester's behalf, starting immediately completed", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Bright Path Clinics ${RUN_ID}`;
    const { clientId } = await createClientAndContractWithSeededAgent(page, clientName);
    const testerEmail = `marco.diaz+${RUN_ID}@brightpath.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    await page.getByRole("button", { name: "Log a request" }).click();
    // fee-logging-and-provisioning ticket added a second dialog ("Log a fee") to this same page
    // with its own "Tester" field — both <dialog> elements exist in the DOM at once (only one
    // open), so scope to the open one rather than the page as a whole.
    const dialog = page.locator("dialog[open]");
    await dialog.getByLabel("Tester").selectOption({ label: testerEmail });
    await dialog.getByLabel("Request type").selectOption({ label: "Other" });
    // Other requires a description.
    await dialog.getByLabel("Description").fill("On-site battery replacement");
    await dialog.getByRole("radio", { name: /Completed/ }).check();
    await dialog.getByRole("button", { name: "Log request" }).click();

    const row = page.getByRole("row", { name: /Other/ });
    await expect(row).toContainText("Completed");
    await expect(row).toContainText(testerEmail);
    await expect(row).toContainText(`Logged by ${SEEDED_USERS.agent.username}`);

    // The Tester's own list shows this Agent-authored, already-Completed Request too
    // (regression: tester-visible list still shows Agent-authored Requests).
    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await page.goto("/client/requests");
    await expect(page.getByRole("row", { name: /Other/ })).toContainText("Completed");
  });

  test("a tester cannot change a request's status", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Kessler & Vance LLP ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const serial = `SN-${RUN_ID}`;
    await addSmartphone(page, contractId, "Pixel 9", serial);
    const simNumber = `+1-555-${RUN_ID}`;
    await addSimCard(page, contractId, simNumber);
    const testerEmail = `helena.voss+${RUN_ID}@kessler.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "SIM Swap", {
      smartphoneOptionLabel: `Pixel 9 — ${serial}`,
      simCardOptionLabel: `${simNumber} — Verizon`,
    });

    // No status controls exist on the Tester's own Requests view — verified directly at the API.
    const requestId = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`);
      const body = (await response.json()) as { id: string }[];
      return body[0].id;
    }, contractId);

    const status = await page.evaluate(
      async ({ contractId, requestId }) => {
        const response = await fetch(`/api/contracts/${contractId}/requests/${requestId}/status`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ status: "IN_PROGRESS" }),
        });
        return response.status;
      },
      { contractId, requestId },
    );
    expect(status).toBe(403);
  });

  test("an agent on a different contract is rejected from changing status or logging a request", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    // This file's own "Bright Path Clinics" test (above) already claims that name with the same
    // RUN_ID — pass a different one so createUnrelatedContract's own Client isn't ambiguous with it.
    const { clientId: otherClientId, contractId: otherContractId } = await createUnrelatedContract(
      page,
      RUN_ID,
      `Solene Cosmetics ${RUN_ID}`,
    );

    const otherTesterEmail = `elise.fabron+${RUN_ID}@solene.example`;
    await addTester(page, otherClientId, otherTesterEmail, "Passw0rd!23");
    const otherSerial = `SN-${RUN_ID}`;
    await addSmartphone(page, otherContractId, "Pixel 9", otherSerial);

    await logout(page);
    await login(page, otherTesterEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Reboot", { smartphoneOptionLabel: `Pixel 9 — ${otherSerial}` });
    const otherRequestId = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`);
      const body = (await response.json()) as { id: string }[];
      return body[0].id;
    }, otherContractId);

    // The seeded Agent cannot change status on that Contract's Request...
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    const statusChangeStatus = await page.evaluate(
      async ({ contractId, requestId }) => {
        const response = await fetch(`/api/contracts/${contractId}/requests/${requestId}/status`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ status: "IN_PROGRESS" }),
        });
        return response.status;
      },
      { contractId: otherContractId, requestId: otherRequestId },
    );
    expect(statusChangeStatus).toBe(403);

    // ...nor log a Request proactively on it.
    const logStatus = await page.evaluate(async (contractId) => {
      const response = await fetch(`/api/contracts/${contractId}/requests`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type: "TOPUP", testerId: crypto.randomUUID() }),
      });
      return response.status;
    }, otherContractId);
    expect(logStatus).toBe(403);
  });
});
