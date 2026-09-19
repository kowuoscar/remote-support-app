import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  addTesterAndSubmitRequestAsAgent,
  createClientAndContractWithSeededAgent,
  login,
  selectContractInSwitcher,
} from "./helpers";

/**
 * An Agent opens a Contract's current-month draft Client Invoice, sees its live base amount and
 * Fee lines "at a glance", and attaches a carrier invoice file (client-invoice-generation ticket,
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
    await addPostpaidSimCard(page, contractId, `+1-555-${RUN_ID.slice(-4)}`, "25.00");
    const testerEmail = `priya.raman+${RUN_ID}@aurora.example`;
    await addTesterAndSubmitRequestAsAgent(page, clientId, clientName, testerEmail, "Topup");

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
    await selectContractInSwitcher(page, clientName);

    await expect(page.getByText("Draft")).toBeVisible();
    // Base amount: the one currently-Active Postpaid SIM's monthly fee. Fees this month: the
    // Topup fee just logged. Total: the sum of both — all three "at a glance" per the ticket's AC.
    await expect(page.getByText("Base amount", { exact: true })).toBeVisible();
    await expect(page.getByText("$25.00", { exact: true })).toBeVisible();
    // "$45.00" appears twice — once in the "Fees this month" summary figure, once as the Fee
    // line's own amount — both correct, so this only needs to confirm at least one renders.
    await expect(page.getByText("$45.00", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("$70.00", { exact: true })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Topup" })).toBeVisible();

    // Attach a carrier invoice file — no dedicated "Choose file" dialog, a visually-hidden native
    // input behind the styled "Attach carrier invoice" button (see
    // AttachCarrierInvoiceFileControl).
    await expect(page.getByText("No carrier invoice files attached yet.")).toBeVisible();
    const [uploadResponse] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes("/client-invoice/files") && resp.request().method() === "POST",
      ),
      page.getByLabel("Attach carrier invoice file").setInputFiles({
        name: "october-carrier-invoice.pdf",
        mimeType: "application/pdf",
        buffer: Buffer.from("not a real pdf"),
      }),
    ]);
    expect(uploadResponse.status()).toBe(201);

    await expect(page.getByText("october-carrier-invoice.pdf")).toBeVisible();
    await expect(page.getByRole("link", { name: "Download" })).toBeVisible();
  });
});
