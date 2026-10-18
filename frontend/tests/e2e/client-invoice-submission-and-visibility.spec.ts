import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  addTester,
  attachCarrierInvoice,
  createAgentWithLogin,
  createClientAndContractWithAgent,
  login,
  logout,
  selectContractInSwitcher,
  selectInSideList,
  selectClientInvoiceContract,
} from "./helpers";

/**
 * The Client Invoice's send/approve lifecycle end to end (client-invoice-submission-and-visibility
 * ticket; agent-sends-the-month): an Agent sends their month's Client Invoices from the month bar, a Tester at that Client sees it read-only and
 * downloads a PDF, and a Manager opens it from the Contract page's invoice summary and approves it on
 * the invoice's detail page (manager-invoice-review-queue spec) — driven against a real backend + Postgres (see
 * playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing decisions.
 * Mirrors tests/e2e/client-invoice-generation.spec.ts's pattern and helpers (this ticket's own
 * follow-on), which already covers the draft-building/attaching-a-file journey this one builds on
 * top of.
 */

const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test.describe("client invoice submission and visibility", () => {
  test("an agent sends a client invoice, a tester views and downloads it, a manager approves it", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    // A fresh Agent with a login and two Contracts, one with a Postpaid SIM and one with nothing: its
    // month is its own, so the bar starts unsent whatever the other specs did to Jordan's.
    const agent = await createAgentWithLogin(page, `Marta Voss ${RUN_ID}`, `marta.voss+${RUN_ID}@agents.example`);
    const clientName = `Kessler & Vance ${RUN_ID}`;
    const { clientId, contractId } = await createClientAndContractWithAgent(page, clientName, agent.agentLabel);
    const emptyClientName = `Quiet Harbour ${RUN_ID}`;
    await createClientAndContractWithAgent(page, emptyClientName, agent.agentLabel);
    const simNumber = `+1-555-${RUN_ID.slice(-4)}`;
    await addPostpaidSimCard(page, contractId, simNumber, "30.00");
    const testerEmail = `charlotte.finch+${RUN_ID}@kesslervance.example`;
    const testerPassword = await addTester(page, clientId, testerEmail);

    // The Contract page summarises its current-month invoice; "Open invoice" works even for a
    // draft, landing on a read-only detail page.
    await page.goto(`/manager/contracts?contract=${contractId}`);
    const draftSummary = page.getByRole("region", { name: "Client Invoice" });
    await expect(draftSummary.getByText("Draft")).toBeVisible();
    await draftSummary.getByRole("link", { name: "Open invoice" }).click();
    await expect(page).toHaveURL(/\/manager\/invoices\/client\/.+/);
    await expect(page.getByRole("heading", { name: `${clientName} — ${agent.agentLabel.split(" · ")[0]}` })).toBeVisible();
    await expect(page.getByText("Draft", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toHaveCount(0);

    // Agent opens the page (creating both drafts as a side effect): the month bar counts them.
    await login(page, agent.email, agent.password);
    await page.goto("/agent/client-invoices");
    const bar = page.getByRole("region", { name: "Month" });
    await expect(bar.getByText("1 of 2 ready")).toBeVisible();
    // The send gate: with the Postpaid SIM's carrier invoice missing, the bar's button is disabled and says why.
    await expect(bar.getByRole("button", { name: "Send all 2 to Manager" })).toBeDisabled();
    await expect(bar.getByText("1 invoice still needs its carrier invoices.")).toBeVisible();

    await selectClientInvoiceContract(page, clientName);
    await expect(page.getByTestId("client-invoice-card").getByText("Draft")).toBeVisible();
    // The month is unsent: the card has no Send of its own, only a line pointing at the bar.
    await expect(page.getByTestId("client-invoice-card").getByRole("button", { name: /Send/ })).toHaveCount(0);
    await expect(page.getByTestId("client-invoice-card").getByText("Sent with the month, from the bar above.")).toBeVisible();
    // The SIM line reads its number, then the Carrier and the flavor.
    await expect(page.getByTestId("client-invoice-card").getByText("Verizon · Postpaid")).toBeVisible();
    // The line's carrier invoice is attached; the Manager will see its check mark.
    await attachCarrierInvoice(page, simNumber);
    await expect(
      page.getByTestId("client-invoice-card").getByRole("img", { name: "Carrier invoice attached" }),
    ).toBeVisible();

    // Every invoice is ready: the Agent sends the whole month through the bar.
    await expect(bar.getByText("2 of 2 ready")).toBeVisible();
    await expect(bar.getByRole("button", { name: "Send all 2 to Manager" })).toBeEnabled();
    await bar.getByRole("button", { name: "Send all 2 to Manager" }).click();
    const [sendResponse] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes("/api/me/client-invoice-months/") && resp.url().endsWith("/send") && resp.request().method() === "POST",
      ),
      bar.getByRole("button", { name: "Confirm send all 2" }).click(),
    ]);
    expect(sendResponse.status()).toBe(200);

    // Sent — the bar says when and has no button; the invoice is no longer editable by the Agent
    // (ticket AC), and a PDF download appears instead.
    const today = new Date().toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
    await expect(bar.getByText(`Sent to Manager on ${today}`)).toBeVisible();
    await expect(bar.getByRole("button")).toHaveCount(0);
    await expect(page.getByTestId("client-invoice-card").getByText("Awaiting approval")).toBeVisible();
    await expect(page.getByRole("button", { name: "Attach carrier invoice" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Send Client Invoice" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Download PDF" })).toBeVisible();

    await logout(page);

    // Tester sees the now-sent invoice read-only and downloads a PDF of it.
    await login(page, testerEmail, testerPassword);
    await page.goto("/client/invoices");
    await selectContractInSwitcher(page, clientName);
    await expect(page.getByText("Awaiting approval")).toBeVisible();
    // "$30.00" appears twice here (base amount and total, since no Fees were logged) — both
    // correct, so this only needs to confirm at least one renders (mirrors
    // client-invoice-generation.spec.ts's note on the same ambiguity).
    await expect(page.getByText("$30.00", { exact: true }).first()).toBeVisible();

    const [download] = await Promise.all([
      page.waitForEvent("download"),
      page.getByRole("link", { name: "Download PDF" }).click(),
    ]);
    expect(download.suggestedFilename()).toMatch(/^client-invoice-.*\.pdf$/);

    await logout(page);

    // Manager opens it from the Contract page's summary — which offers no approve action of its
    // own — and approves it on the invoice's detail page.
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto(`/manager/contracts?contract=${contractId}`);
    const summary = page.getByRole("region", { name: "Client Invoice" });
    await expect(summary.getByText("Awaiting approval")).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toHaveCount(0);
    await summary.getByRole("link", { name: "Open invoice" }).click();
    await expect(page).toHaveURL(/\/manager\/invoices\/client\/.+/);
    const detailUrl = page.url();
    await expect(page.getByRole("list", { name: "Postpaid SIM Cards" }).getByText("Verizon · Postpaid")).toBeVisible();
    // Read-only check mark on the line with its file; nothing to attach or remove here.
    await expect(
      page.getByRole("list", { name: "Postpaid SIM Cards" }).getByRole("img", { name: "Carrier invoice attached" }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: /Attach|Remove/ })).toHaveCount(0);

    const [approveResponse] = await Promise.all([
      page.waitForResponse(
        (resp) =>
          resp.url().includes("/api/client-invoices/") &&
          resp.url().endsWith("/approve") &&
          resp.request().method() === "POST",
      ),
      page.getByRole("button", { name: "Approve" }).click(),
    ]);
    expect(approveResponse.status()).toBe(200);

    await expect(page.getByText("Approved", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toHaveCount(0);

    // The Contract page's summary reflects the approval and still links to the same invoice.
    await page.goto(`/manager/contracts?contract=${contractId}`);
    await expect(summary.getByText("Approved", { exact: true })).toBeVisible();
    await summary.getByRole("link", { name: "Open invoice" }).click();
    await expect(page).toHaveURL(detailUrl);

    // The Manager's step through the Agents side list (manager-reviews-by-agent): the fresh Agent's
    // month has the empty Contract's invoice left, which the month's own button approves.
    const agentName = agent.agentLabel.split(" · ")[0];
    await page.goto("/manager/invoices");
    await selectInSideList(page, "Agents", new RegExp(`^${agentName} 1 waiting$`));
    const monthHeader = page.getByRole("region", { name: "Month" });
    await expect(monthHeader).toContainText("1 of 2 to review");
    await monthHeader.getByRole("button", { name: "Approve the 1 left to review" }).click();
    await monthHeader.getByRole("button", { name: "Confirm approve" }).click();
    await expect(monthHeader).toContainText("2 of 2 approved");
    await expect(page.getByRole("row").filter({ hasText: emptyClientName })).toContainText("Approved");
  });
});
