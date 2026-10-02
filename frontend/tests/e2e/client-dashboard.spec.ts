import { test, expect, type Page } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  addSmartphone,
  addTester,
  createContractWithTester,
  login,
  logout,
  selectContractInSwitcher,
  submitRequestAsTester,
} from "./helpers";

/**
 * The Client dashboard end to end (real-client-dashboard spec; tickets client-dashboard-identity,
 * -fleet-and-requests and -latest-invoices), against a real backend + Postgres at the
 * accessibility-tree level. A fresh Client per run keeps every figure ours alone: the cases of the
 * first describe share one Contract and run in order, because the invoice moves from "No invoices
 * yet" to "Awaiting approval" to "Approved".
 */

const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

const invoiceRow = (page: Page, clientName: string) =>
  page.getByTestId("latest-invoices-card").getByRole("listitem").filter({ hasText: clientName });

test.describe.serial("a Tester's dashboard follows the Contract's invoice", () => {
  test.setTimeout(180_000);

  const clientName = `Dashboard Client ${RUN_ID}`;
  const testerEmail = `dashboard.tester+${RUN_ID}@example.com`;
  let contractId = "";
  let testerPassword = "";

  test("tester-dashboard-shows-client-name-fleet-and-open-requests-then-no-invoices-yet", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    ({ contractId, testerPassword } = await createContractWithTester(page, clientName, testerEmail));
    await addSmartphone(page, contractId, "Pixel 8", `SER-${RUN_ID}`);
    await addPostpaidSimCard(page, contractId, `+1-555-${RUN_ID.slice(-4)}`, "30.00");
    await logout(page);

    await login(page, testerEmail, testerPassword);
    await submitRequestAsTester(page, "Other", { description: "Screen flickers" });
    await page.goto("/client");

    await expect(page.locator("header").first()).toContainText(clientName);
    await expect(page.getByTestId("active-fleet-stat")).toContainText("2");
    await expect(page.getByTestId("open-requests-stat")).toContainText("1");
    const row = invoiceRow(page, clientName);
    await expect(row).toContainText("United States");
    await expect(row).toContainText("No invoices yet");
    await expect(page.getByRole("link", { name: "Open Invoices" })).toHaveAttribute("href", "/client/invoices");
    await expect(page.getByText("Demo data", { exact: false })).toHaveCount(0);
  });

  test("agent-sends-invoice-and-row-reads-awaiting-approval-with-total", async ({ page }) => {
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/client-invoices");
    await selectContractInSwitcher(page, clientName);
    await page.getByRole("button", { name: "Send Client Invoice" }).click();
    const [sendResponse] = await Promise.all([
      page.waitForResponse((resp) => resp.url().includes("/client-invoice/send") && resp.request().method() === "POST"),
      page.getByRole("button", { name: "Confirm send" }).click(),
    ]);
    expect(sendResponse.status()).toBe(200);
    await logout(page);

    await login(page, testerEmail, testerPassword);
    await page.goto("/client");
    const row = invoiceRow(page, clientName);
    await expect(row).toContainText("Awaiting approval");
    await expect(row).toContainText("$30.00");
    await expect(row).not.toContainText("No invoices yet");
  });

  test("manager-approves-and-row-reads-approved", async ({ page }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto(`/manager/contracts/${contractId}`);
    await page.getByRole("region", { name: "Client Invoice" }).getByRole("link", { name: "Open invoice" }).click();
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
    await logout(page);

    await login(page, testerEmail, testerPassword);
    await page.goto("/client");
    const row = invoiceRow(page, clientName);
    await expect(row).toContainText("Approved");
    await expect(row).toContainText("$30.00");
  });
});

test("client-with-no-contract-shows-name-zero-counts-and-no-contracts-yet", async ({ page }) => {
  const clientName = `Contractless Client ${RUN_ID}`;
  const testerEmail = `contractless.tester+${RUN_ID}@example.com`;

  await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
  await page.goto("/manager/clients");
  await page.getByRole("button", { name: "Add client" }).first().click();
  await page.getByLabel("Client name").fill(clientName);
  await page.getByRole("dialog").getByRole("button", { name: "Add client" }).click();
  await expect(page.getByRole("cell", { name: clientName })).toBeVisible();
  const clientId = (await page.getByRole("link", { name: clientName }).getAttribute("href"))!.split("/").pop()!;
  const testerPassword = await addTester(page, clientId, testerEmail);
  await logout(page);

  await login(page, testerEmail, testerPassword);
  await page.goto("/client");

  await expect(page.locator("header").first()).toContainText(clientName);
  await expect(page.getByTestId("active-fleet-stat")).toContainText("0");
  await expect(page.getByTestId("open-requests-stat")).toContainText("0");
  const card = page.getByTestId("latest-invoices-card");
  await expect(card).toContainText("No Contracts yet");
  await expect(card).toContainText("Your Contracts and their invoices will show up here once the Manager sets them up.");
});
