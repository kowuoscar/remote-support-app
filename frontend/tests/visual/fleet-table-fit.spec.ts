import { expect, test, type Page } from "@playwright/test";
import { gotoWithSession } from "./helpers";

/**
 * The Fleet tables fit the detail area beside the side list (side-lists-and-tabs spec, "Fleet"):
 * at the narrowest width the column exists (1280, `xl`) and at 1440, no table scrolls sideways,
 * for each Contract the stub backend holds and for both tabs, and with a 60-character Plan name in
 * every Plan cell. Measured, not screenshotted: the wrapper's `scrollWidth` equals its `clientWidth`.
 */
const LONG_PLAN =
  "Unlimited Premium Postpaid Plan with Roaming and 5G Datapack";
expect(LONG_PLAN).toHaveLength(60);

/** Puts the long Plan name into every Plan cell (the third column): the stub has one only on the Tester's second Contract. */
async function putLongPlanInCells(page: Page) {
  await page
    .getByRole("tabpanel")
    .locator('td:nth-child(3) span[translate="no"][title]')
    .evaluateAll((cells, name) => {
      for (const cell of cells) {
        cell.textContent = name;
        cell.setAttribute("title", name);
      }
    }, LONG_PLAN);
}
const PAGES = [
  {
    name: "Agent Fleet",
    path: "/agent/fleet",
    session: "visual-agent-session",
    list: "Clients",
  },
  {
    name: "Manager Contracts",
    path: "/manager/contracts?contract=22222222-0000-0000-0000-000000000001",
    session: "visual-manager-session",
    list: "Clients",
  },
  {
    name: "Tester Fleet",
    path: "/client/fleet",
    session: "visual-tester-session",
    list: "Contracts",
  },
] as const;

/** The open tab's table wrapper, or null when the tab shows its empty state instead. */
async function tableOverflow(page: Page) {
  const wrapper = page.getByRole("tabpanel").locator("div.overflow-x-auto");
  if ((await wrapper.count()) === 0) return null;
  return wrapper.evaluate((el) => ({
    scrollWidth: el.scrollWidth,
    clientWidth: el.clientWidth,
  }));
}

for (const width of [1280, 1440]) {
  for (const target of PAGES) {
    test(`${target.name} tables fit at ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await gotoWithSession(page, target.path, target.session);
      const rows = page
        .getByRole("navigation", { name: target.list })
        .getByRole("link");
      const count = await rows.count();
      for (let i = 0; i < Math.max(count, 1); i++) {
        if (count > 0) await rows.nth(i).click();
        for (const tab of ["SIM Cards", "Smartphones"]) {
          await page.getByRole("tab", { name: new RegExp(`^${tab}`) }).click();
          for (const plan of [null, LONG_PLAN]) {
            if (plan) await putLongPlanInCells(page);
            const overflow = await tableOverflow(page);
            if (overflow)
              expect(
                overflow.scrollWidth,
                `${target.name} row ${i} ${tab} ${plan ? "long plan" : ""}`,
              ).toBe(overflow.clientWidth);
          }
        }
      }
    });
  }
}
