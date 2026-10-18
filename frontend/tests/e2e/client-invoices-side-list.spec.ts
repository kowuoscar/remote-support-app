import { test, expect } from "@playwright/test";
import {
  SEEDED_USERS,
  addPostpaidSimCard,
  createClientAndContractWithSeededAgent,
  login,
  logout,
  attachCarrierInvoicesForEveryPostpaidSim,
} from "./helpers";

/**
 * The Agent picks a Client's invoice from a side list on a desktop (client-invoices-side-list spec,
 * contract-side-list-on-desktop ticket), driven against a real backend + Postgres. Prior art:
 * send-a-client-invoice-back.spec.ts for the `setViewportSize` switches. Never put "Agent" in a
 * Client name here: getByLabel("Agent") matches option text elsewhere.
 */
const RUN_ID = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
const ALPHA = `Sidelist Alpha ${RUN_ID}`;
const ZULU = `Sidelist Zulu ${RUN_ID}`;
const SIM_NUMBER = `+1-555-${RUN_ID.slice(-4)}`;

test.describe("client invoices side list", () => {
  test("a desktop Agent picks an invoice from the column; a phone keeps the switcher", async ({ page, context }) => {
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    // Created in reverse alphabetical order, so the column's order is not creation order.
    await createClientAndContractWithSeededAgent(page, ZULU);
    const { contractId: alphaContractId } = await createClientAndContractWithSeededAgent(page, ALPHA);
    // Only Alpha's invoice carries a Postpaid SIM line: its row is how a swap is seen.
    await addPostpaidSimCard(page, alphaContractId, SIM_NUMBER, "25.00");
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);

    const requests: string[] = [];
    page.on("request", (request) => {
      if (request.isNavigationRequest() || request.url().includes("/api/") || request.url().includes("_rsc")) {
        requests.push(request.url());
      }
    });

    await page.goto("/agent/client-invoices");
    const clients = page.getByRole("navigation", { name: "Clients" });
    await expect(clients).toBeVisible();
    await expect(page.locator('[aria-haspopup="listbox"]')).toBeHidden();

    // Both rows, in alphabetical order; the first row in the column is the selected one.
    const names = (await clients.getByRole("link").allTextContents()).map((text) => text.replace(/\s*Draft(?: Ready| \d+ files? missing)$/, ""));
    expect(names.indexOf(ALPHA)).toBeGreaterThanOrEqual(0);
    expect(names.indexOf(ALPHA)).toBeLessThan(names.indexOf(ZULU));
    await expect(clients.getByRole("link").first()).toHaveAttribute("aria-current", "true");
    await expect.poll(async () => (await clients.boundingBox())?.width).toBe(256);

    // A click swaps the invoice in place: no document, no backend request.
    const alpha = clients.getByRole("link", { name: `${ALPHA} Draft 1 file missing`, exact: true });
    const zulu = clients.getByRole("link", { name: `${ZULU} Draft Ready`, exact: true });
    await alpha.click();
    await expect(alpha).toHaveAttribute("aria-current", "true");
    await expect(page.getByRole("cell", { name: SIM_NUMBER })).toBeVisible();
    requests.length = 0;
    await zulu.click();
    await expect(zulu).toHaveAttribute("aria-current", "true");
    await expect(alpha).not.toHaveAttribute("aria-current");
    await expect(page.getByRole("cell", { name: SIM_NUMBER })).toHaveCount(0);
    await page.waitForTimeout(300);
    expect(requests).toEqual([]);
    await alpha.click();
    await expect(page.getByRole("cell", { name: SIM_NUMBER })).toBeVisible();

    // Tab and Enter: every row is reachable by keyboard and shows the focus ring (the ring is
    // :focus-visible, so it takes a key press, not a programmatic focus after a mouse click).
    await alpha.focus();
    await page.keyboard.press("Tab");
    const next = clients.getByRole("link").nth(names.indexOf(ALPHA) + 1);
    await expect(next).toBeFocused();
    await expect(next).toHaveCSS("outline-style", "solid");
    await page.keyboard.press("Shift+Tab");
    await expect(alpha).toBeFocused();
    await expect(alpha).toHaveCSS("outline-style", "solid");
    await page.keyboard.press("Tab");
    await page.keyboard.press("Enter");
    await expect(next).toHaveAttribute("aria-current", "true");
    await alpha.click();

    // A modified click and a middle click fall through to the href: a new tab, no swap in place.
    for (const click of [
      () => zulu.click({ modifiers: ["ControlOrMeta"] }),
      () => zulu.click({ button: "middle" }),
    ]) {
      const [opened] = await Promise.all([context.waitForEvent("page"), click()]);
      await expect(alpha).toHaveAttribute("aria-current", "true");
      await expect(opened).toHaveURL(/\/agent\/client-invoices\?contract=.+/);
      await opened.close();
    }

    // Scrolling (story 7, 8): in a short window the invoice is taller than the window and the
    // column's rows overflow the column. Picking a row returns the document to the top and leaves
    // the column's own scroll where it was.
    await page.setViewportSize({ width: 1440, height: 200 });
    const metrics = () =>
      clients.evaluate((el) => ({ scrollHeight: el.scrollHeight, clientHeight: el.clientHeight, scrollTop: el.scrollTop }));
    await expect.poll(async () => (await metrics()).clientHeight).toBeLessThan(200);
    const sized = await metrics();
    expect(sized.scrollHeight).toBeGreaterThan(sized.clientHeight);
    await zulu.scrollIntoViewIfNeeded();
    const columnScroll = (await metrics()).scrollTop;
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(0);
    await zulu.click();
    await expect(zulu).toHaveAttribute("aria-current", "true");
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
    expect((await metrics()).scrollTop).toBe(columnScroll);

    // Phone: no "Clients" landmark, the switcher present and working, nothing scrolls sideways.
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(clients).toHaveCount(0);
    await page.locator('[aria-haspopup="listbox"]').click();
    await page.getByRole("option", { name: new RegExp(ZULU) }).click();
    await expect(page.getByRole("cell", { name: SIM_NUMBER })).toHaveCount(0);
    await page.locator('[aria-haspopup="listbox"]').click();
    await page.getByRole("option", { name: new RegExp(ALPHA) }).click();
    await expect(page.getByRole("cell", { name: SIM_NUMBER })).toBeVisible();
    await expect
      .poll(() => page.evaluate(() => document.documentElement.scrollWidth === window.innerWidth))
      .toBe(true);
  });
  test("the selected Client is kept in the address: reload, unknown id, scrolled into view, Back", async ({ page }) => {
    test.setTimeout(240_000);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const names: string[] = [];
    const ids: string[] = [];
    for (let i = 1; i <= 14; i++) {
      const name = `Sidelist Addr ${String(i).padStart(2, "0")} ${RUN_ID}`;
      const { contractId } = await createClientAndContractWithSeededAgent(page, name);
      names.push(name);
      ids.push(contractId);
    }
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);

    await page.setViewportSize({ width: 1440, height: 500 });
    await page.goto("/agent/client-invoices");
    const clients = page.getByRole("navigation", { name: "Clients" });
    const row = (name: string) => clients.getByRole("link", { name: `${name} Draft Ready`, exact: true });

    // A pick writes ?contract=<id> with no request and no history entry; a reload reopens it.
    const requests: string[] = [];
    page.on("request", (request) => {
      if (request.isNavigationRequest() || request.url().includes("/api/") || request.url().includes("_rsc")) {
        requests.push(request.url());
      }
    });
    const historyBefore = await page.evaluate(() => window.history.length);
    requests.length = 0;
    await row(names[2]).click();
    await expect(row(names[2])).toHaveAttribute("aria-current", "true");
    await expect(page).toHaveURL(new RegExp(`\\?contract=${ids[2]}$`));
    await page.waitForTimeout(300);
    // No document, no API read, and no render of the page for the new address. (The shell's own
    // nav links may re-prefetch their bare routes after the address changes; those carry no contract.)
    expect(requests.filter((url) => !url.includes("_rsc") || url.includes("contract="))).toEqual([]);
    expect(await page.evaluate(() => window.history.length)).toBe(historyBefore);
    await page.reload();
    await expect(row(names[2])).toHaveAttribute("aria-current", "true");

    // An unknown id opens the first row, quietly.
    await page.goto("/agent/client-invoices?contract=nonsense");
    await expect(clients.getByRole("link").first()).toHaveAttribute("aria-current", "true");

    // The last of 14: that row is current and within the column's own viewport.
    await page.goto(`/agent/client-invoices?contract=${ids[13]}`);
    await expect(row(names[13])).toHaveAttribute("aria-current", "true");
    await expect
      .poll(async () => {
        const navBox = await clients.boundingBox();
        const rowBox = await row(names[13]).boundingBox();
        return !!navBox && !!rowBox && rowBox.y >= navBox.y && rowBox.y + rowBox.height <= navBox.y + navBox.height;
      })
      .toBe(true);

    // Back leaves the page: the pick added no entry.
    await page.goto("/agent/requests");
    await page.goto("/agent/client-invoices");
    await row(names[4]).click();
    await expect(page).toHaveURL(new RegExp(`\\?contract=${ids[4]}$`));
    await page.goBack();
    await expect(page).toHaveURL(/\/agent\/requests$/);

    // Phone: a switcher choice is kept in the address across a reload.
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/agent/client-invoices");
    await page.locator('[aria-haspopup="listbox"]').click();
    await page.getByRole("option", { name: new RegExp(names[6]) }).click();
    await expect(page).toHaveURL(new RegExp(`\\?contract=${ids[6]}$`));
    await page.reload();
    await expect(page.locator('[aria-haspopup="listbox"]')).toContainText(names[6]);
  });

  test("Open on a sent-back invoice, then the breadcrumb, returns with that Client selected", async ({ page }) => {
    const name = `Sidelist Zzz Return ${RUN_ID}`;
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const { contractId } = await createClientAndContractWithSeededAgent(page, name);
    await addPostpaidSimCard(page, contractId, `+1-555-${RUN_ID.slice(-4)}`, "25.00");
    await logout(page);

    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto(`/agent/client-invoices?contract=${contractId}`);
    await attachCarrierInvoicesForEveryPostpaidSim(page);
    await page.getByRole("button", { name: "Send Client Invoice" }).click();
    await page.getByRole("button", { name: "Confirm send" }).click();
    await expect(page.getByTestId("client-invoice-card").getByText("Awaiting approval")).toBeVisible();
    await logout(page);

    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/invoices");
    await page.getByRole("link", { name: new RegExp(`^Review Client Invoice: ${name} `) }).click();
    await page.getByRole("button", { name: "Send back" }).click();
    await page.getByRole("textbox", { name: "Reason for sending back" }).fill("Please recheck the base amount.");
    await page.getByRole("button", { name: "Confirm send back" }).click();
    await expect(page.getByText(/Sent back to the Agent on/)).toBeVisible();
    await logout(page);

    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/client-invoices");
    const section = page.getByRole("region", { name: "Sent back to you" });
    await section.getByRole("row", { name: new RegExp(name) }).getByRole("link", { name: /^Open / }).click();
    await expect(page).toHaveURL(/\/agent\/client-invoices\/.+/);
    await page.getByRole("navigation", { name: "Breadcrumb" }).getByRole("link", { name: "Client Invoices" }).click();
    await expect(page).toHaveURL(new RegExp(`/agent/client-invoices\\?contract=${contractId}$`));
    await expect(
      page.getByRole("navigation", { name: "Clients" }).getByRole("link", { name: `${name} Sent back Ready`, exact: true }),
    ).toHaveAttribute("aria-current", "true");

    // Leave nothing behind: a sent-back invoice would show in later specs' "Sent back to you".
    await attachCarrierInvoicesForEveryPostpaidSim(page);
    await page.getByRole("button", { name: "Resend Client Invoice" }).click();
    await page.getByRole("button", { name: "Confirm resend" }).click();
    await expect(page.getByTestId("client-invoice-card").getByText("Awaiting approval")).toBeVisible();
    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/invoices");
    await page.getByRole("link", { name: new RegExp(`^Review Client Invoice: ${name} `) }).click();
    await page.getByRole("button", { name: "Approve" }).click();
    await expect(page.getByText("Approved", { exact: true })).toBeVisible();
  });
});

test.describe("client invoices side list row layout", () => {
  test("name left and badge right on one line; a 60-character name is cut, the row stays one line (side-list-row-one-line)", async ({ page }) => {
    const longName = `Sidelong ${RUN_ID} `.padEnd(60, "x");
    expect(longName).toHaveLength(60);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await createClientAndContractWithSeededAgent(page, longName);
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);

    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto("/agent/client-invoices");
    const clients = page.getByRole("navigation", { name: "Clients" });
    await expect.poll(async () => (await clients.boundingBox())?.width).toBe(256);

    const rows = clients.getByRole("link");
    const count = await rows.count();
    expect(count).toBeGreaterThan(0);
    for (let i = 0; i < count; i++) {
      const row = rows.nth(i);
      const nameBox = await row.locator("span[title]").boundingBox();
      const badgeBox = await row.getByText(/^(Draft|Awaiting approval|Approved|Sent back)$/).first().boundingBox();
      const rowBox = await row.boundingBox();
      // Name and badge share a line; the name is on the left, the badge inside the right inset.
      expect(nameBox!.y).toBeLessThan(badgeBox!.y + badgeBox!.height);
      expect(badgeBox!.y).toBeLessThan(nameBox!.y + nameBox!.height);
      expect(badgeBox!.x).toBeGreaterThanOrEqual(nameBox!.x + nameBox!.width);
      expect(badgeBox!.x + badgeBox!.width).toBeLessThanOrEqual(rowBox!.x + rowBox!.width - 8);
    }

    const longRow = clients.getByRole("link", { name: `${longName} Draft Ready`, exact: true });
    const longNameEl = longRow.locator("span[title]");
    await expect(longNameEl).toHaveAttribute("title", longName);
    expect(await longNameEl.evaluate((el) => el.scrollWidth > el.clientWidth)).toBe(true);
    const longBox = await longRow.boundingBox();
    const nameBox = await longNameEl.boundingBox();
    const badgeBox = await longRow.getByText("Draft", { exact: true }).boundingBox();
    // Two lines high: the badge's line, the readiness line (gap-y-1) and the row's vertical padding
    // (py-2), no more; the long name stays on its one truncated line beside its badge.
    const readyBox = await longRow.getByText("Ready", { exact: true }).boundingBox();
    expect(readyBox!.y).toBeGreaterThanOrEqual(nameBox!.y + nameBox!.height);
    expect(longBox!.height).toBeLessThanOrEqual(badgeBox!.height + 4 + readyBox!.height + 16 + 1);
    expect(nameBox!.height).toBeLessThanOrEqual(badgeBox!.height);
    expect(badgeBox!.x + badgeBox!.width).toBeLessThanOrEqual(longBox!.x + longBox!.width - 8);
    expect(badgeBox!.x).toBeGreaterThanOrEqual(longBox!.x);
  });
});

test.describe("client invoices side list row status", () => {
  test("each row reads Draft on arrival (side-list-row-status)", async ({ page }) => {
    const first = `Sidestatus Alpha ${RUN_ID}`;
    const second = `Sidestatus Zulu ${RUN_ID}`;
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await createClientAndContractWithSeededAgent(page, second);
    await createClientAndContractWithSeededAgent(page, first);
    await logout(page);
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);

    await page.goto("/agent/client-invoices");
    const clients = page.getByRole("navigation", { name: "Clients" });
    for (const name of [first, second]) {
      // The link's accessible name is the Client name followed by the status label.
      const row = clients.getByRole("link", { name: `${name} Draft Ready`, exact: true });
      await expect(row).toBeVisible();
      await expect(row.getByText("Draft", { exact: true })).toBeVisible();
    }
  });
});

test.describe("client invoices side list sent back", () => {
  test("a Manager send-back reads once on the row with the list first; a resend clears both", async ({ page }) => {
    const sentBackName = `Sidesent Zulu ${RUN_ID}`;
    const otherName = `Sidesent Alpha ${RUN_ID}`;
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    const { contractId } = await createClientAndContractWithSeededAgent(page, sentBackName);
    await createClientAndContractWithSeededAgent(page, otherName);
    await logout(page);

    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto(`/agent/client-invoices?contract=${contractId}`);
    const clients = page.getByRole("navigation", { name: "Clients" });
    const card = page.getByTestId("client-invoice-card");
    await attachCarrierInvoicesForEveryPostpaidSim(page);
    await page.getByRole("button", { name: "Send Client Invoice" }).click();
    await page.getByRole("button", { name: "Confirm send" }).click();
    await expect(clients.getByRole("link", { name: `${sentBackName} Awaiting approval`, exact: true })).toBeVisible();
    await logout(page);

    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/invoices");
    await page.getByRole("link", { name: new RegExp(`^Review Client Invoice: ${sentBackName} `) }).click();
    await page.getByRole("button", { name: "Send back" }).click();
    await page.getByRole("textbox", { name: "Reason for sending back" }).fill("Please recheck the base amount.");
    await page.getByRole("button", { name: "Confirm send back" }).click();
    await expect(page.getByText(/Sent back to the Agent on/)).toBeVisible();
    await logout(page);

    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto(`/agent/client-invoices?contract=${contractId}`);
    // The row reads "Sent back" once (its own badge, no second mark); no other row carries it.
    const row = clients.getByRole("link", { name: `${sentBackName} Sent back Ready`, exact: true });
    await expect(row).toBeVisible();
    await expect(row.getByText("Sent back")).toHaveCount(1);
    await expect(clients.getByText("Sent back")).toHaveCount(1);

    // The list sits above the invoice whichever Client is selected.
    const region = page.getByRole("region", { name: "Sent back to you" });
    for (const name of [sentBackName, otherName]) {
      await clients.getByRole("link", { name: new RegExp(`^${name}`) }).click();
      await expect(card).toBeVisible();
      const regionBox = await region.boundingBox();
      const cardBox = await card.boundingBox();
      expect(regionBox!.y + regionBox!.height).toBeLessThanOrEqual(cardBox!.y);
    }

    // The Agent resends: the badge returns to "Awaiting approval", no "Sent back" is left on the
    // row and the region is gone, with the same Client still selected.
    await row.click();
    await attachCarrierInvoicesForEveryPostpaidSim(page);
    await page.getByRole("button", { name: "Resend Client Invoice" }).click();
    await page.getByRole("button", { name: "Confirm resend" }).click();
    const resent = clients.getByRole("link", { name: `${sentBackName} Awaiting approval`, exact: true });
    await expect(resent).toBeVisible();
    await expect(resent).toHaveAttribute("aria-current", "true");
    await expect(clients.getByText("Sent back")).toHaveCount(0);
    await expect(region).toHaveCount(0);

    // Leave nothing behind for send-a-client-invoice-back.spec.ts: the Manager approves it.
    await logout(page);
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/invoices");
    await page.getByRole("link", { name: new RegExp(`^Review Client Invoice: ${sentBackName} `) }).click();
    await page.getByRole("button", { name: "Approve" }).click();
    await expect(page.getByText("Approved", { exact: true })).toBeVisible();
  });
});
