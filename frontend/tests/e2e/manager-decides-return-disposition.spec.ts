import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addSimCard,
  addSmartphone,
  addTester,
  approveFromPendingRequests,
  createClientAndContractWithSeededAgent,
  completeReturnRequest,
  fetchSimCards,
  fetchSmartphones,
  login,
  logout,
  pendingRequestRow,
  selectContractInSwitcher,
  submitRequestAsTester,
} from "./helpers";

/**
 * The Manager decides Dispositions for a Return holding company-owned units
 * (manager-decides-return-disposition ticket, spec.md Solution's Approval/Disposition/Completion
 * tables), driven against a real backend + Postgres (see playwright.e2e.config.ts), at the
 * accessibility-tree level per spec.md's Testing decisions. Mirrors
 * tests/e2e/return-client-owned-smartphones.spec.ts and
 * tests/e2e/manager-approves-requests.spec.ts's patterns. The seeded agent@example.com login
 * resolves to the "Jordan Ellis" Agent (V5 migration), USD.
 */
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("manager decides return disposition", () => {
  test("a tester returns a company-owned smartphone and a sim card, the manager posts one and cancels the other, and the agent completes with a date", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Solstice Retail ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);

    const serial = `SN-${RUN_ID}`;
    await addSmartphone(page, contractId, "iPhone 15", serial);
    const number = `+1-555-${RUN_ID}`;
    await addSimCard(page, contractId, number);

    const testerEmail = `dara.novak+${RUN_ID}@solstice.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Return", { returnedUnitLabels: [`iPhone 15 — ${serial}`, number] });

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

    await approveFromPendingRequests(page, clientName, "Return");

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
    await completeReturnRequest(page, row, { [number.replace(/\+/g, "\\+")]: "2026-08-15" });

    // API-level assertions for the Fleet's post-completion state (mirrors
    // return-client-owned-smartphones.spec.ts's own precedent — text-content on a Fleet row is
    // the wrong tool once a Smartphone can also show an installed SIM's number).
    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);

    const smartphones = await fetchSmartphones(page, contractId);
    const returnedPhone = smartphones.find((phone) => phone.serial === serial)!;
    expect(returnedPhone.status).toBe("RETIRED");

    const simCards = await fetchSimCards(page, contractId);
    const returnedSim = simCards.find((sim) => sim.number === number)!;
    expect(returnedSim.status).toBe("RETIRED");
    expect(returnedSim.cancellationEffectiveDate).toBe("2026-08-15");
  });
});
