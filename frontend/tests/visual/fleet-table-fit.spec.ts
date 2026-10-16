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

/** Opens the page's first row and the tab, as a person would. */
async function openTab(page: Page, target: (typeof PAGES)[number], tab: string) {
  await gotoWithSession(page, target.path, target.session);
  await page.getByRole("tab", { name: new RegExp(`^${tab}`) }).click();
}

// Layout A (ui-side-lists-fleet-table-layout): the page itself never scrolls sideways, whatever the width.
for (const width of [375, 390, 768, 1024, 1279, 1440]) {
  for (const target of PAGES) {
    test(`${target.name} has no sideways page scroll at ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      for (const tab of ["SIM Cards", "Smartphones"]) {
        await openTab(page, target, tab);
        const [scrollWidth, clientWidth] = await page.evaluate(() => [
          document.documentElement.scrollWidth,
          document.documentElement.clientWidth,
        ]);
        expect(scrollWidth, `${target.name} ${tab}`).toBe(clientWidth);
      }
    });
  }
}

// Below xl the dense table keeps its 720px minimum and scrolls inside its wrapper; a SIM number
// never breaks across lines.
for (const width of [375, 768]) {
  for (const target of PAGES) {
    test(`${target.name} SIM Cards table scrolls inside its wrapper at ${width}px, numbers on one line`, async ({
      page,
    }) => {
      await page.setViewportSize({ width, height: 900 });
      await openTab(page, target, "SIM Cards");
      const wrapper = page.getByRole("tabpanel").locator("div.overflow-x-auto");
      const { scrollWidth, clientWidth } = await wrapper.evaluate((el) => ({
        scrollWidth: el.scrollWidth,
        clientWidth: el.clientWidth,
      }));
      expect(scrollWidth).toBeGreaterThanOrEqual(720);
      if (width < 720 + 48) expect(scrollWidth).toBeGreaterThan(clientWidth);
      const lines = await wrapper.evaluate((el) =>
        [...el.querySelectorAll("tbody tr td:first-child")].map((cell) => {
          const walker = document.createTreeWalker(cell, NodeFilter.SHOW_TEXT);
          for (let node = walker.nextNode(); node; node = walker.nextNode()) {
            if (!node.textContent?.trim().startsWith("+")) continue;
            const range = document.createRange();
            range.selectNodeContents(node);
            return new Set([...range.getClientRects()].map((rect) => Math.round(rect.top))).size;
          }
          return 0;
        }),
      );
      expect(lines.length).toBeGreaterThan(0);
      expect(lines.every((count) => count === 1)).toBe(true);
    });
  }
}

// Plan and Carrier names are cut at their column's width: one that fits is whole, a longer one is
// cut with the full name in `title`.
test("Plan and Carrier names are cut only when their column has no room", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  const names = async () =>
    page
      .getByRole("tabpanel")
      .locator('td span[translate="no"][title]')
      .evaluateAll((spans) =>
        spans.map((span) => ({
          text: span.textContent ?? "",
          title: span.getAttribute("title") ?? "",
          cut: span.scrollWidth > span.clientWidth,
        })),
      );

  await openTab(page, PAGES[0], "SIM Cards");
  const fits = await names();
  expect(fits.length).toBeGreaterThan(0);
  expect(fits.filter((name) => name.cut)).toEqual([]);

  await openTab(page, PAGES[2], "SIM Cards");
  await putLongPlanInCells(page);
  const long = (await names()).filter((name) => name.title === LONG_PLAN);
  expect(long.length).toBeGreaterThan(0);
  expect(long.every((name) => name.cut)).toBe(true);
  expect((await names()).filter((name) => name.title !== LONG_PLAN && name.cut)).toEqual([]);
});
