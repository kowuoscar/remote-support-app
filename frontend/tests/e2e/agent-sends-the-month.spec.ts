import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  attachCarrierInvoice,
  createAgentWithLogin,
  createClientAndContractWithAgent,
  login,
  logout,
  selectClientInvoiceContract,
} from "./helpers";

/**
 * The card's side of agent-sends-the-month, end to end against a real backend + Postgres: with the
 * month unsent no card has a Send; once the bar has sent the month, a sent-back invoice is resent
 * alone from its card (the bar's date unchanged) and a Contract added after the send is sent alone,
 * gated by its own carrier invoices. A fresh Agent, so the month is its own (Jordan's is sent by the
 * global setup, which is why every other spec's new Contract is a late one).
 */
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
const AGENT_NAME = `Ines Okoro ${RUN_ID}`;
const REASON = `Please recheck the carrier invoice. ${RUN_ID}`;

test.describe("the card's Send defers to the month bar", () => {
  test("a send-back is resent alone, a Contract added after the send is sent alone, and no card sends before the month", async ({
    page,
  }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const agent = await createAgentWithLogin(page, AGENT_NAME, `ines.okoro+${RUN_ID}@agents.example`);
    const firstName = `Harlow Mills ${RUN_ID}`;
    const { contractId: firstContractId } = await createClientAndContractWithAgent(page, firstName, agent.agentLabel);
    const firstSim = `+1-555-${RUN_ID.slice(-4)}`;
    await addPostpaidSimCard(page, firstContractId, firstSim, "30.00");
    const emptyName = `Quiet Pier ${RUN_ID}`;
    await createClientAndContractWithAgent(page, emptyName, agent.agentLabel);

    // Before the month is sent: neither card has a Send, only the line pointing at the bar.
    await login(page, agent.email, agent.password);
    await page.goto("/agent/client-invoices");
    const bar = page.getByRole("region", { name: "Month" });
    await expect(bar.getByText("1 of 2 ready")).toBeVisible();
    const card = page.getByTestId("client-invoice-card");
    for (const name of [firstName, emptyName]) {
      await selectClientInvoiceContract(page, name);
      await expect(card.getByText("Sent with the month, from the bar above.")).toBeVisible();
      await expect(card.getByRole("button", { name: /Send/ })).toHaveCount(0);
    }
    await selectClientInvoiceContract(page, firstName);
    await attachCarrierInvoice(page, firstSim);
    await expect(bar.getByText("2 of 2 ready")).toBeVisible();
    await bar.getByRole("button", { name: "Send all 2 to Manager" }).click();
    await bar.getByRole("button", { name: "Confirm send all 2" }).click();
    const today = new Date().toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
    const sentOn = bar.getByText(`Sent to Manager on ${today}`);
    await expect(sentOn).toBeVisible();

    // The Manager sends the first invoice back.
    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/invoices");
    await page
      .getByRole("link", { name: new RegExp(`^Review Client Invoice: ${firstName} — ${AGENT_NAME},`) })
      .click();
    await page.getByRole("button", { name: "Send back" }).click();
    await page.getByRole("textbox", { name: "Reason for sending back" }).fill(REASON);
    await page.getByRole("button", { name: "Confirm send back" }).click();
    await expect(page.getByText(/Sent back to the Agent on/)).toBeVisible();

    // The Agent resends that invoice alone from its card; the bar keeps its date and the other
    // invoice is untouched.
    await logout(page);
    await login(page, agent.email, agent.password);
    await page.goto("/agent/client-invoices");
    await expect(sentOn).toBeVisible();
    await selectClientInvoiceContract(page, firstName);
    await expect(card.getByRole("button", { name: "Send Client Invoice", exact: true })).toHaveCount(0);
    await card.getByRole("button", { name: "Resend Client Invoice" }).click();
    await card.getByRole("button", { name: "Confirm resend" }).click();
    await expect(card.getByText("Awaiting approval")).toBeVisible();
    await expect(sentOn).toBeVisible();
    await selectClientInvoiceContract(page, emptyName);
    await expect(card.getByText("Awaiting approval")).toBeVisible();

    // A Contract added after the month was sent is a late one: its card sends it alone, gated by
    // its own carrier invoices.
    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const lateName = `Late Harbour ${RUN_ID}`;
    const { contractId: lateContractId } = await createClientAndContractWithAgent(page, lateName, agent.agentLabel);
    const lateSim = `+1-555-${RUN_ID.slice(-3)}9`;
    await addPostpaidSimCard(page, lateContractId, lateSim, "18.00");
    await logout(page);
    await login(page, agent.email, agent.password);
    await page.goto("/agent/client-invoices");
    await selectClientInvoiceContract(page, lateName);
    await expect(card.getByRole("button", { name: "Send Client Invoice", exact: true })).toBeDisabled();
    await expect(card.getByText("1 Postpaid SIM still needs its carrier invoice.")).toBeVisible();
    await attachCarrierInvoice(page, lateSim);
    await expect(card.getByRole("button", { name: "Send Client Invoice", exact: true })).toBeEnabled();
    await card.getByRole("button", { name: "Send Client Invoice", exact: true }).click();
    await card.getByRole("button", { name: "Confirm send" }).click();
    await expect(card.getByText("Awaiting approval")).toBeVisible();
    await expect(sentOn).toBeVisible();
  });
});
