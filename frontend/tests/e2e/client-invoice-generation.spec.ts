import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  addTesterAndSubmitRequestAsAgent,
  attachCarrierInvoice,
  createClientAndContractWithSeededAgent,
  login,
  selectClientInvoiceContract,
} from "./helpers";

/**
 * An Agent opens a Contract's current-month draft Client Invoice, sees its live base amount and
 * Fee lines "at a glance", and attaches a carrier invoice file per Postpaid SIM (client-invoice-generation ticket,
 * user stories 22-23), driven against a real backend + Postgres (see playwright.e2e.config.ts), at
 * the accessibility-tree level per spec.md's Testing decisions. Mirrors
 * tests/e2e/fee-logging-and-provisioning.spec.ts's pattern. The seeded agent@example.com login
 * resolves to the "Jordan Ellis" Agent (V5 migration), USD.
 */

// Date.now() alone can collide across spec files: Playwright's collection phase can import
// several spec files within the same millisecond (see fee-logging-and-provisioning.spec.ts's
// note), so this suite uses its own RUN_ID rather than sharing one.
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("client invoice generation", () => {
  test("an agent views a contract's draft client invoice with correct amounts and attaches a file", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const clientName = `Aurora Retail Group ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithSeededAgent(page, clientName);
    const simNumber = `+1-555-${RUN_ID.slice(-4)}`;
    await addPostpaidSimCard(page, contractId, simNumber, "25.00");
    const testerEmail = `priya.raman+${RUN_ID}@aurora.example`;
    // reboot-and-topup-details ticket: a Topup Request now names the SIM Card it tops up, and
    // Verizon (its Carrier here) has active Topup Options (V22 migration).
    await addTesterAndSubmitRequestAsAgent(page, clientId, clientName, testerEmail, "Topup", {
      simCardOptionLabel: `${simNumber} — Verizon`,
    });

    const row = page.getByRole("row", { name: /Topup/ });
    await row.getByRole("button", { name: "Mark In Progress" }).click();
    await row.getByRole("button", { name: "Mark Completed" }).click();
    await row.getByLabel(/Fee amount/).fill("45.00");
    const [feeResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/fees") && resp.request().method() === "POST"),
      row.getByRole("button", { name: "Mark Completed" }).click(),
    ]);
    expect(feeResponse.status()).toBe(201);

    // Viewing the Client Invoice for the first time this month creates the draft as a side
    // effect (client-invoice-generation ticket's get-or-create mechanic) — there's no separate
    // "create" step to perform first.
    await page.goto("/agent/client-invoices");
    await selectClientInvoiceContract(page, clientName);

    await expect(page.getByTestId("client-invoice-card").getByText("Draft")).toBeVisible();
    // Base amount: the one currently-Active Postpaid SIM's monthly fee. Fees this month: the
    // Topup fee just logged. Total: the sum of both — all three "at a glance" per the ticket's AC.
    await expect(page.getByText("Base amount", { exact: true })).toBeVisible();
    // "$25.00" appears twice — once in the "Base amount" summary figure, once as this one
    // Postpaid SIM's own monthly-fee line in the "Postpaid SIM Cards" breakdown
    // (cancelled-sim-billed-through-its-month ticket) — both correct, so this only needs to
    // confirm at least one renders.
    await expect(page.getByText("$25.00", { exact: true }).first()).toBeVisible();
    // "$45.00" appears twice — once in the "Fees this month" summary figure, once as the Fee
    // line's own amount — both correct, so this only needs to confirm at least one renders.
    await expect(page.getByText("$45.00", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("$70.00", { exact: true })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Topup" })).toBeVisible();

    // Attach a carrier invoice file through the "Attach carrier invoice" form: one file per
    // Postpaid SIM line (carrier-invoice-per-sim).
    await expect(page.getByText("No carrier invoice files attached yet.")).toBeVisible();
    // Nothing attached yet: the SIM line shows no check mark.
    await expect(page.getByRole("img", { name: "Carrier invoice attached" })).toHaveCount(0);
    await attachCarrierInvoice(page, simNumber, "october-carrier-invoice.PDF");

    // The file is listed under its computed name, not the uploaded one
    // (carrier-invoice-computed-name): {Client} - {Month Year} - {Carrier} - {SIM number}{.ext}.
    const monthYear = new Date().toLocaleString("en-US", { month: "long", year: "numeric", timeZone: "UTC" });
    await expect(page.getByText(`${clientName} - ${monthYear} - Verizon - ${simNumber}.pdf`)).toBeVisible();
    await expect(page.getByText("october-carrier-invoice")).toHaveCount(0);
    // The line's check mark appears once its file is attached (carrier-invoice-check-mark).
    await expect(
      page.getByTestId("client-invoice-card").getByRole("img", { name: "Carrier invoice attached" }),
    ).toBeVisible();
    // Every Postpaid SIM line now has its file: nothing is left to attach.
    await expect(page.getByRole("button", { name: "Attach carrier invoice" })).toBeDisabled();
    await expect(page.getByRole("link", { name: "Download" })).toBeVisible();
  });
});
