import { test, expect, type Page } from "@playwright/test";
import { SEEDED_USERS, login, selectInSideList } from "./helpers";

/**
 * The Carriers page (carrier-catalog spec; agent-maintains-carriers ticket; side-lists-and-tabs
 * spec, carriers-side-list ticket), driven against a real backend + Postgres at the
 * accessibility-tree level. The seeded agent@example.com login is "Jordan Ellis", in the United
 * States, whose Country is seeded with Verizon, T-Mobile and AT&T (and an archived Sprint). Every
 * Carrier of the Country is a row of the "Carriers" side list, archived ones last; the selected one
 * shows its offers on the right. Names carry a run suffix: the e2e database outlives a run, and a
 * Carrier name is unique among its Country's active Carriers.
 */

function carriersNav(page: Page) {
  return page.getByRole("navigation", { name: "Carriers", exact: true });
}

/** The side list row of a Carrier, by its name (any "Archived" badge text after it is ignored). */
function carrierLink(page: Page, name: string) {
  return carriersNav(page)
    .getByRole("link")
    .filter({ has: page.getByText(name, { exact: true }) });
}

/** The page's header renders once above the column and once above the dropdown; one is displayed. */
function visible(page: Page, label: string) {
  return page.getByLabel(label).filter({ visible: true });
}

async function addCarrier(page: Page, name: string) {
  await page.getByRole("button", { name: "Add carrier" }).first().click();
  await page.getByRole("dialog").getByLabel("Carrier name").fill(name);
  await page.getByRole("dialog").getByRole("button", { name: "Add carrier" }).click();
}

async function noSidewaysScroll(page: Page) {
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth === window.innerWidth))
    .toBe(true);
}

test("an Agent adds, renames and archives a Carrier, and finds it again at the bottom, marked", async ({ page }) => {
  const run = Date.now().toString(36);
  const name = `Ridgeline ${run}`;
  const renamed = `Ridgeline Wireless ${run}`;

  await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
  await page.getByRole("link", { name: "Carriers" }).first().click();
  await expect(page).toHaveURL(/\/agent\/carriers$/);
  await expect(page.getByRole("heading", { level: 1, name: "Carriers" })).toBeVisible();
  await expect(carrierLink(page, "Verizon")).toBeVisible();
  // Archived Carriers are always in the column, at the bottom, marked: the seeded Sprint is one.
  await expect(carrierLink(page, "Sprint")).toBeVisible();
  await expect(carrierLink(page, "Sprint").getByText("Archived", { exact: true })).toBeVisible();
  const names = await carriersNav(page).getByRole("link").allTextContents();
  const firstArchived = names.findIndex((text) => /Archived$/.test(text.trim()));
  expect(firstArchived).toBeGreaterThan(0);
  for (const text of names.slice(firstArchived)) expect(text.trim()).toMatch(/Archived$/);

  // A Carrier just added is the selected one, and the address says so.
  await addCarrier(page, name);
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(carrierLink(page, name)).toHaveAttribute("aria-current", "true");
  await expect(page).toHaveURL(/carrier=/);
  await expect(page.getByRole("heading", { level: 2, name })).toBeVisible();

  // A second active Carrier with the same name is refused inline, in the dialog.
  await addCarrier(page, "verizon");
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText("already");
  await page.getByRole("dialog").getByRole("button", { name: "Cancel" }).click();

  await page.getByRole("button", { name: `Rename ${name}` }).click();
  await page.getByRole("dialog").getByLabel("Carrier name").fill(renamed);
  await page.getByRole("dialog").getByRole("button", { name: "Save name" }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  // The mutation's refresh keeps the selection (and the address).
  await expect(carrierLink(page, renamed)).toHaveAttribute("aria-current", "true");
  await expect(page).toHaveURL(/carrier=/);
  await expect(carrierLink(page, name)).toHaveCount(0);

  // Archived, the Carrier stays selected, moves to the bottom and is marked; nothing under it can change.
  await page.getByRole("button", { name: `Archive ${renamed}` }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Archive carrier" }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  const archived = carrierLink(page, renamed);
  await expect(archived.getByText("Archived", { exact: true })).toBeVisible();
  await expect(archived).toHaveAttribute("aria-current", "true");
  const after = await carriersNav(page).getByRole("link").allTextContents();
  const archivedAt = after.findIndex((text) => text.includes(renamed));
  for (const text of after.slice(archivedAt)) expect(text.trim()).toMatch(/Archived$/);
  await expect(page.getByRole("heading", { level: 2, name: renamed })).toBeVisible();
  await expect(page.getByText(/^Archived [A-Z][a-z]{2} \d{1,2}, \d{4}$/)).toBeVisible();
  await expect(page.getByRole("button", { name: new RegExp(`^(Rename|Archive|Add a) `) })).toHaveCount(0);
});

test("a pick shows that Carrier's offers in place, keeps the address across a reload, and ignores an unknown one", async ({
  page,
}) => {
  await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
  await page.goto("/agent/carriers");
  // The first Carrier is current on arrival.
  await expect(carriersNav(page).getByRole("link").first()).toHaveAttribute("aria-current", "true");

  const requests: string[] = [];
  page.on("request", (request) => {
    // The rail's own links prefetch on their own schedule; a pick must cause no other request.
    const prefetch = request.headers()["next-router-prefetch"] !== undefined;
    if (!prefetch && (request.isNavigationRequest() || request.url().includes("/api/") || request.url().includes("_rsc"))) {
      requests.push(request.url());
    }
  });
  await selectInSideList(page, "Carriers", "Verizon");
  await expect(carrierLink(page, "Verizon")).toHaveAttribute("aria-current", "true");
  await expect(page.getByRole("list", { name: "Verizon postpaid plans" })).toBeVisible();
  await expect(page.getByRole("list", { name: "AT&T postpaid plans" })).toHaveCount(0);
  await expect(page).toHaveURL(/carrier=/);
  // A pick makes no request.
  expect(requests).toEqual([]);

  await page.reload();
  await expect(carrierLink(page, "Verizon")).toHaveAttribute("aria-current", "true");
  await expect(page.getByRole("list", { name: "Verizon postpaid plans" })).toBeVisible();

  // A value naming no Carrier is ignored: the first row opens, with no error.
  await page.goto("/agent/carriers?carrier=nonsense");
  await expect(carriersNav(page).getByRole("link").first()).toHaveAttribute("aria-current", "true");
});

test("an Agent adds a Topup Option and a Postpaid Plan, edits the Plan's price and archives the Option", async ({
  page,
}) => {
  const carrier = `Harbor Mobile ${Date.now().toString(36)}`;
  const dialog = page.getByRole("dialog");

  await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
  await page.goto("/agent/carriers");
  // The seeded Carriers already carry the seeded offers, priced in the Country's currency.
  await selectInSideList(page, "Carriers", "AT&T");
  await expect(
    page.getByRole("list", { name: "AT&T postpaid plans" }).getByRole("listitem").first(),
  ).toContainText("$");

  // The new Carrier is selected as soon as it is added.
  await addCarrier(page, carrier);
  await expect(dialog).toBeHidden();
  await expect(carrierLink(page, carrier)).toHaveAttribute("aria-current", "true");
  const options = page.getByRole("list", { name: `${carrier} topup options` });
  const plans = page.getByRole("list", { name: `${carrier} postpaid plans` });

  await page.getByRole("button", { name: `Add a topup option to ${carrier}` }).click();
  await expect(dialog.getByLabel("Price (USD)")).toBeVisible();
  await dialog.getByLabel("Name").fill("Refill 20");
  await dialog.getByLabel("Price (USD)").fill("0");
  await dialog.getByRole("button", { name: "Add topup option" }).click();
  await expect(dialog.getByRole("alert")).toContainText("above zero");
  await dialog.getByLabel("Price (USD)").fill("20");
  await dialog.getByRole("button", { name: "Add topup option" }).click();
  await expect(dialog).toBeHidden();
  await expect(options.getByRole("listitem").filter({ hasText: "Refill 20" })).toContainText("$20.00");

  await page.getByRole("button", { name: `Add a postpaid plan to ${carrier}` }).click();
  await dialog.getByLabel("Name").fill("Unlimited Basic");
  await dialog.getByLabel("Monthly price (USD)").fill("55");
  await dialog.getByRole("button", { name: "Add postpaid plan" }).click();
  await expect(dialog).toBeHidden();
  await expect(plans.getByRole("listitem").filter({ hasText: "Unlimited Basic" })).toContainText("$55.00");

  await plans.getByRole("button", { name: "Edit Unlimited Basic" }).click();
  await dialog.getByLabel("Monthly price (USD)").fill("59.99");
  await dialog.getByRole("button", { name: "Save changes" }).click();
  await expect(dialog).toBeHidden();
  await expect(plans.getByRole("listitem").filter({ hasText: "Unlimited Basic" })).toContainText("$59.99");

  await options.getByRole("button", { name: "Archive Refill 20" }).click();
  await dialog.getByRole("button", { name: "Archive topup option" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByText("Refill 20")).toHaveCount(0);

  // "Show archived" counts and reveals the selected Carrier's archived offers.
  await page.getByRole("checkbox", { name: /Show archived/ }).check();
  const archivedOption = options.getByRole("listitem").filter({ hasText: "Refill 20" });
  await expect(archivedOption).toContainText("Archived");
  await expect(archivedOption.getByRole("button")).toHaveCount(0);

  // Leave the shared e2e database's catalog as it was found.
  await page.getByRole("button", { name: `Archive ${carrier}` }).click();
  await dialog.getByRole("button", { name: "Archive carrier" }).click();
  await expect(dialog).toBeHidden();
});

test("the Manager switches the Country filter and sees that Country's Carriers, first one selected", async ({
  page,
}) => {
  const telcel = `Telcel ${Date.now().toString(36)}`;

  await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
  await page.getByRole("link", { name: "Carriers" }).first().click();
  await expect(page).toHaveURL(/\/manager\/carriers/);

  await visible(page, "Country").selectOption({ label: "Mexico" });
  await expect(page).toHaveURL(/country=MEXICO/);
  await addCarrier(page, telcel);
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(carrierLink(page, telcel)).toBeVisible();
  await expect(page).toHaveURL(/carrier=/);

  await visible(page, "Country").selectOption({ label: "United States" });
  await expect(page).toHaveURL(/country=UNITED_STATES/);
  // The new Country opens on its first Carrier; the previous Country's pick is dropped from the address.
  await expect(page).not.toHaveURL(/carrier=/);
  await expect(carrierLink(page, "Verizon")).toBeVisible();
  await expect(carriersNav(page).getByRole("link").first()).toHaveAttribute("aria-current", "true");
  await expect(carrierLink(page, telcel)).toHaveCount(0);

  await visible(page, "Country").selectOption({ label: "Mexico" });
  await expect(carrierLink(page, telcel)).toBeVisible();

  // Leave the shared e2e database's Mexican catalog as it was found.
  await selectInSideList(page, "Carriers", telcel);
  await page.getByRole("button", { name: `Archive ${telcel}` }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Archive carrier" }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(carrierLink(page, telcel).getByText("Archived", { exact: true })).toBeVisible();
});

test("on a phone a Carrier dropdown replaces the column, with nothing scrolling sideways", async ({ page }) => {
  await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/agent/carriers");

  await expect(carriersNav(page)).toBeHidden();
  const trigger = page.locator('[aria-haspopup="listbox"]');
  await expect(trigger).toBeVisible();
  await expect(trigger).toContainText("AT&T");
  await trigger.click();
  await page.getByRole("option", { name: /^Verizon/ }).click();
  await expect(trigger).toContainText("Verizon");
  await expect(page.getByRole("list", { name: "Verizon postpaid plans" })).toBeVisible();
  await expect(page).toHaveURL(/carrier=/);
  await noSidewaysScroll(page);

  // At every width exactly one of the column and the dropdown is displayed.
  for (const width of [375, 768, 1024, 1279]) {
    await page.setViewportSize({ width, height: 844 });
    await expect(carriersNav(page)).toBeHidden();
    await expect(trigger).toBeVisible();
    await noSidewaysScroll(page);
  }
  for (const width of [1280, 1440]) {
    await page.setViewportSize({ width, height: 844 });
    await expect(carriersNav(page)).toBeVisible();
    await expect(trigger).toBeHidden();
    await noSidewaysScroll(page);
  }
});
