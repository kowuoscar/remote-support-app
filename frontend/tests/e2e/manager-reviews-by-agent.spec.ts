import { test, expect, type Page } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  attachCarrierInvoice,
  createAgentWithLogin,
  createClientAndContractWithAgent,
  login,
  logout,
  selectClientInvoiceContract,
  selectInSideList,
} from "./helpers";

/**
 * The Manager's Invoices page by Agent (manager-reviews-by-agent), end to end against a real backend
 * + Postgres: the Agents side list with its counts and All Agents first, an Agent's month as a list,
 * the Agent Invoice row, and the dropdown at 390px. Every test makes a fresh Agent, so the month is its
 * own (Jordan's is sent by the global setup and other specs add Contracts to it).
 */
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
const TODAY = new Date().toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
const THIS_MONTH = new Date().toLocaleDateString("en-US", { month: "long", year: "numeric" });

/**
 * A fresh Agent with two Contracts (Harlow with one Postpaid SIM, Quiet Pier with nothing) whose
 * month is sent: two Client Invoices to review. Leaves the page signed in as the Agent.
 */
async function aFreshAgentWhoSentTheMonth(page: Page, tag: string) {
  const agentName = `${tag} Okoro ${RUN_ID}`;
  const harlow = `Harlow Mills ${tag} ${RUN_ID}`;
  const quiet = `Quiet Pier ${tag} ${RUN_ID}`;
  await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
  const agent = await createAgentWithLogin(page, agentName, `${tag.toLowerCase()}.okoro+${RUN_ID}@agents.example`);
  const { contractId } = await createClientAndContractWithAgent(page, harlow, agent.agentLabel);
  const sim = `+1-555-${RUN_ID.slice(-3)}${tag.charCodeAt(0) % 10}`;
  await addPostpaidSimCard(page, contractId, sim, "30.00");
  await createClientAndContractWithAgent(page, quiet, agent.agentLabel);
  await logout(page);

  await login(page, agent.email, agent.password);
  await page.goto("/agent/client-invoices");
  await selectClientInvoiceContract(page, harlow);
  await attachCarrierInvoice(page, sim);
  const bar = page.getByRole("region", { name: "Month" });
  await expect(bar.getByText("2 of 2 ready")).toBeVisible();
  await bar.getByRole("button", { name: "Send all 2 to Manager" }).click();
  await bar.getByRole("button", { name: "Confirm send all 2" }).click();
  await expect(bar.getByText(`Sent to Manager on ${TODAY}`)).toBeVisible();
  return { agentName, harlow, quiet, agent };
}

test.describe("the Manager reviews Client Invoices by Agent", () => {
  test("the side list counts what waits, All Agents is the default, a pick shows the month and survives a reload", async ({
    page,
  }) => {
    const { agentName, harlow, quiet } = await aFreshAgentWhoSentTheMonth(page, "Ines");
    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);

    await page.goto("/manager/invoices");
    const agents = page.getByRole("navigation", { name: "Agents", exact: true });
    await expect(agents.getByRole("link", { name: /^All Agents \d+ waiting$/ })).toHaveAttribute("aria-current", "true");
    await expect(page.getByRole("tablist", { name: "Filter the Review Queue by invoice type" })).toBeVisible();
    await expect(agents.getByRole("link", { name: `${agentName} 2 waiting`, exact: true })).toBeVisible();

    await selectInSideList(page, "Agents", agentName);
    await expect(page).toHaveURL(/\/manager\/invoices\?agent=[0-9a-f-]+$/);
    const header = page.getByRole("region", { name: "Month" });
    await expect(header).toContainText(`${THIS_MONTH} · Sent to you on ${TODAY} · 2 of 2 to review`);
    const clients = page.getByRole("row").filter({ has: page.getByRole("link", { name: /^Review Client Invoice:/ }) });
    await expect(clients).toHaveCount(2);
    await expect(clients.nth(0)).toContainText(harlow);
    await expect(clients.nth(1)).toContainText(quiet);
    await expect(clients.nth(0)).toContainText("To review");
    await expect(page.getByRole("combobox", { name: "Month" }).locator("option:checked")).toHaveText(
      `${THIS_MONTH} · 2 waiting`,
    );

    // A reload keeps the pick.
    await page.reload();
    await expect(agents.getByRole("link", { name: `${agentName} 2 waiting`, exact: true })).toHaveAttribute(
      "aria-current",
      "true",
    );
    await expect(page.getByRole("region", { name: "Month" })).toContainText("2 of 2 to review");

    // A row's Review opens today's detail page.
    await page.getByRole("link", { name: `Review Client Invoice: ${harlow} — ${agentName}, ${THIS_MONTH}` }).click();
    await expect(page).toHaveURL(/\/manager\/invoices\/client\/[0-9a-f-]+$/);

    // An address naming no Agent of the tenant opens All Agents.
    await page.goto("/manager/invoices?agent=00000000-0000-0000-0000-00000000dead");
    await expect(agents.getByRole("link", { name: /^All Agents/ })).toHaveAttribute("aria-current", "true");
  });

  test("the Agent Invoice is its own row under the month once sent", async ({ page }) => {
    const { agentName } = await aFreshAgentWhoSentTheMonth(page, "Tessa");
    await page.goto("/agent/my-invoice");
    await page.getByRole("button", { name: "Send Agent Invoice" }).click();
    await page.getByRole("button", { name: "Confirm send" }).click();
    await expect(page.getByText("Awaiting approval")).toBeVisible();
    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);

    await page.goto("/manager/invoices");
    await selectInSideList(page, "Agents", new RegExp(`^${agentName} 3 waiting$`));
    const row = page.getByRole("region", { name: "Agent Invoice" });
    await expect(row.getByText("Awaiting approval")).toBeVisible();
    await row.getByRole("link", { name: `Review Agent Invoice: ${agentName}, ${THIS_MONTH}` }).click();
    await expect(page).toHaveURL(/\/manager\/invoices\/agent\/[0-9a-f-]+$/);
  });

  test("below xl the Agents are a dropdown and nothing scrolls sideways", async ({ page }) => {
    const { agentName, harlow } = await aFreshAgentWhoSentTheMonth(page, "Wren");
    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);

    const noSidewaysScroll = () =>
      expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth === window.innerWidth)).toBe(true);

    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/manager/invoices");
    await expect(page.getByRole("navigation", { name: "Agents", exact: true })).toHaveCount(0);
    await page.locator('[aria-haspopup="listbox"]').click();
    await expect(page.getByRole("option", { name: new RegExp(`^${agentName}\\s*2 waiting$`) })).toBeVisible();
    await page.getByRole("option", { name: new RegExp(`^${agentName}`) }).click();
    await expect(page.getByRole("region", { name: "Month" })).toContainText("2 of 2 to review");
    await noSidewaysScroll();

    await page.getByRole("link", { name: `Review Client Invoice: ${harlow} — ${agentName}, ${THIS_MONTH}` }).click();
    await expect(page).toHaveURL(/\/manager\/invoices\/client\/[0-9a-f-]+$/);
    await noSidewaysScroll();
  });
});
