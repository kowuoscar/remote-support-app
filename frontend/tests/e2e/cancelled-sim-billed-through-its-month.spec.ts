import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addSimCard,
  addTester,
  approveFromPendingRequests,
  createClientAndContractWithSeededAgent,
  completeReturnRequest,
  login,
  logout,
  pendingRequestRow,
  selectContractInSwitcher,
  submitRequestAsTester,
} from "./helpers";

/**
 * A cancelled Postpaid SIM is billed through the month of its cancellation date
 * (cancelled-sim-billed-through-its-month ticket, spec.md Solution's "Billing a cancelled
 * Postpaid SIM"; ticket AC "The draft Client Invoice view lists a cancelled SIM Card it still
 * bills, marked with its cancellation date"), driven against a real backend + Postgres, at the
 * accessibility-tree level per spec.md's Testing decisions. Extends the Return flow already
 * covered end to end by tests/e2e/manager-decides-return-disposition.spec.ts (mirrors its helpers
 * closely) with this ticket's own assertion: the draft Client Invoice for the current month still
 * shows the cancelled SIM Card's fee. The seeded agent@example.com login resolves to the "Jordan
 * Ellis" Agent (V5 migration), USD.
 */

/** Today's date as a bare ISO day, UTC — a cancellation effective today is always "on or after
 * this month's first day", so it always still bills the current month regardless of what day the
 * suite happens to run on. */
function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Mirrors `frontend/lib/format.ts`'s `formatLocalDate` — the Fleet tables and the draft Client
 * Invoice's Postpaid SIM table render a bare `LocalDate` through it (design-review finding on
 * this feature's finisher pass), not the raw ISO string this test used to assert.
 */
function formatLocalDate(localDate: string): string {
  return new Date(`${localDate}T00:00:00Z`).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  });
}

const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("cancelled sim billed through its month", () => {
  test("a cancelled postpaid sim card still shows on the draft client invoice for the current month, marked with its cancellation date", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Havenwood Supply ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);

    const number = `+1-555-${RUN_ID}`;
    // A Postpaid SIM Card on the seeded US Carrier "Verizon"'s "Unlimited Welcome" Plan ($65.00).
    await addSimCard(page, contractId, number, "Verizon", "POSTPAID", "Unlimited Welcome");

    const testerEmail = `ines.duarte+${RUN_ID}@havenwood.example`;
    await addTester(page, clientId, testerEmail, "Passw0rd!23");

    await logout(page);
    await login(page, testerEmail, "Passw0rd!23");
    await submitRequestAsTester(page, "Return", { returnedUnitLabels: [number] });

    // A SIM Card is always company-owned, so this Return waits at Pending Approval (spec.md
    // Solution's Approval table).
    await expect(page.getByRole("row", { name: /Return/ })).toContainText("Pending Approval");

    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/requests");
    const pendingRow = pendingRequestRow(page, clientName, "Return");
    await expect(pendingRow).toContainText(clientName);
    // A SIM Card's only fitting Disposition today is Cancelled, preselected (ticket AC).
    await expect(pendingRow.getByLabel(new RegExp(number.replace(/\+/g, "\\+")))).toHaveValue("CANCELLED");

    await approveFromPendingRequests(page, clientName, "Return");

    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/requests");
    await selectContractInSwitcher(page, clientName);

    const row = page.getByRole("row", { name: /Return/ });
    await expect(row).toContainText("Cancelled");
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await expect(row).toContainText("In Progress");

    const effectiveDate = todayIso();
    await completeReturnRequest(page, row, { [number.replace(/\+/g, "\\+")]: effectiveDate });

    // The AC under test: the draft Client Invoice for the current month still lists this SIM
    // Card, marked with its cancellation date, and its fee is still in the base amount — a
    // cancellation date of "today" is always on or after this month's first day.
    await page.goto("/agent/client-invoices");
    await selectContractInSwitcher(page, clientName);

    await expect(page.getByRole("heading", { name: "Postpaid SIM Cards" })).toBeVisible();
    const simRow = page.getByRole("row", { name: new RegExp(number.replace(/\+/g, "\\+")) });
    await expect(simRow).toContainText(`Cancelled ${formatLocalDate(effectiveDate)}`);
    await expect(page.getByText("Base amount", { exact: true })).toBeVisible();
    await expect(page.getByText("$65.00").first()).toBeVisible();
  });
});
