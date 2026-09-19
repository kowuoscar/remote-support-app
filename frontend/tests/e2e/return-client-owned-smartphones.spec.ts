import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addSimCard,
  addSmartphone,
  addTester,
  createClientAndContractWithSeededAgent,
  completeReturnRequest,
  fetchSimCards,
  fetchSmartphones,
  installSimCard,
  login,
  logout,
  selectContractInSwitcher,
  submitRequestAsTester,
} from "./helpers";

/**
 * A Return of a Client-owned Smartphone needs no approval, and completing it retires the
 * Smartphone and uninstalls its SIM Cards (return-client-owned-smartphones ticket), driven
 * against a real backend + Postgres (see playwright.e2e.config.ts), at the accessibility-tree
 * level per spec.md's Testing decisions. Mirrors tests/e2e/sim-swap-moves.spec.ts's pattern. The
 * seeded agent@example.com login resolves to the "Jordan Ellis" Agent (V5 migration), USD.
 */
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("return client-owned smartphones", () => {
  test("a tester returns a client-owned smartphone, the agent completes it, and the fleet shows it retired with its sim card uninstalled", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Harborlight Retail ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);

    const serial = `SN-${RUN_ID}`;
    await addSmartphone(page, contractId, "Pixel 8", serial, "Client");
    const number = `+1-555-${RUN_ID}`;
    await addSimCard(page, contractId, number);
    await installSimCard(page, contractId, number, `Pixel 8 — ${serial}`);

    const testerEmail = `imane.diallo+${RUN_ID}@harborlight.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Return", { returnedUnitLabels: [`Pixel 8 — ${serial}`] });

    await expect(page.getByRole("row", { name: /Return/ })).toContainText("Submitted");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Return/ });
    await expect(row).toContainText("Posted to Client");
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");
    await completeReturnRequest(page, row);

    await page.goto("/agent/fleet");
    await selectContractInSwitcher(page, clientName);

    // API-level assertions for the Fleet's post-completion state (mirrors sim-swap-moves.spec.ts):
    // a Smartphone's Fleet row shows any SIM Card installed in it (sim-installed-in-smartphone
    // ticket), so a text-content assertion on the Smartphone row could pass on a stale row while
    // the fetched state itself is the ground truth.
    const smartphones = await fetchSmartphones(page, contractId);
    const returnedPhone = smartphones.find((phone) => phone.serial === serial)!;
    expect(returnedPhone.status).toBe("RETIRED");

    const simCards = await fetchSimCards(page, contractId);
    const returnedSim = simCards.find((sim) => sim.number === number)!;
    expect(returnedSim.status).toBe("ACTIVE");
    expect(returnedSim.installedInSmartphoneId).toBeUndefined();
  });
});
