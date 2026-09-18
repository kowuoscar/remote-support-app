import { test, expect, type Page } from "@playwright/test";

/**
 * The Carriers page (carrier-catalog spec; agent-maintains-carriers ticket), driven against a
 * real backend + Postgres at the accessibility-tree level. The seeded agent@example.com login is
 * "Jordan Ellis", in the United States, whose Country is seeded with Verizon, T-Mobile and AT&T
 * (and an archived Sprint). Names carry a run suffix: the e2e database outlives a run, and a
 * Carrier name is unique among its Country's active Carriers.
 */
const SEEDED_USERS = {
  manager: { username: "manager@example.com", password: "ChangeMe123!" },
  agent: { username: "agent@example.com", password: "AgentDemo123!" },
} as const;

async function login(page: Page, username: string, password: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

function carriersList(page: Page) {
  return page.getByRole("list", { name: "Carriers" });
}

function carrierRow(page: Page, name: string) {
  return carriersList(page).getByRole("listitem").filter({ has: page.getByText(name, { exact: true }) });
}

async function addCarrier(page: Page, name: string) {
  await page.getByRole("button", { name: "Add carrier" }).first().click();
  await page.getByRole("dialog").getByLabel("Carrier name").fill(name);
  await page.getByRole("dialog").getByRole("button", { name: "Add carrier" }).click();
}

test("an Agent adds, renames and archives a Carrier, and finds it again under show archived", async ({
  page,
}) => {
  const run = Date.now().toString(36);
  const name = `Ridgeline ${run}`;
  const renamed = `Ridgeline Wireless ${run}`;

  await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
  await page.getByRole("link", { name: "Carriers" }).first().click();
  await expect(page).toHaveURL(/\/agent\/carriers$/);
  await expect(page.getByRole("heading", { level: 1, name: "Carriers" })).toBeVisible();
  await expect(carrierRow(page, "Verizon")).toBeVisible();
  // The seeded Sprint is archived, so it stays hidden until asked for.
  await expect(carrierRow(page, "Sprint")).toHaveCount(0);

  await addCarrier(page, name);
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(carrierRow(page, name)).toBeVisible();

  // A second active Carrier with the same name is refused inline, in the dialog.
  await addCarrier(page, "verizon");
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText("already");
  await page.getByRole("dialog").getByRole("button", { name: "Cancel" }).click();

  await carrierRow(page, name).getByRole("button", { name: `Rename ${name}` }).click();
  await page.getByRole("dialog").getByLabel("Carrier name").fill(renamed);
  await page.getByRole("dialog").getByRole("button", { name: "Save name" }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(carrierRow(page, renamed)).toBeVisible();
  await expect(carrierRow(page, name)).toHaveCount(0);

  await carrierRow(page, renamed).getByRole("button", { name: `Archive ${renamed}` }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Archive carrier" }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(carrierRow(page, renamed)).toHaveCount(0);

  await page.getByRole("checkbox", { name: "Show archived" }).check();
  const archivedRow = carrierRow(page, renamed);
  await expect(archivedRow).toBeVisible();
  await expect(archivedRow.getByText("Archived", { exact: true })).toBeVisible();
  await expect(archivedRow.getByRole("button")).toHaveCount(0);
  await expect(carrierRow(page, "Sprint")).toBeVisible();
});

test("the Manager switches the Country filter and sees that Country's Carriers", async ({ page }) => {
  const telcel = `Telcel ${Date.now().toString(36)}`;

  await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
  await page.getByRole("link", { name: "Carriers" }).first().click();
  await expect(page).toHaveURL(/\/manager\/carriers/);

  await page.getByLabel("Country").selectOption({ label: "Mexico" });
  await expect(page).toHaveURL(/country=MEXICO/);
  await addCarrier(page, telcel);
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(carrierRow(page, telcel)).toBeVisible();

  await page.getByLabel("Country").selectOption({ label: "United States" });
  await expect(page).toHaveURL(/country=UNITED_STATES/);
  await expect(carrierRow(page, "Verizon")).toBeVisible();
  await expect(carrierRow(page, telcel)).toHaveCount(0);

  await page.getByLabel("Country").selectOption({ label: "Mexico" });
  await expect(carrierRow(page, telcel)).toBeVisible();

  // Leave the shared e2e database's Mexican catalog as it was found.
  await carrierRow(page, telcel).getByRole("button", { name: `Archive ${telcel}` }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Archive carrier" }).click();
  await expect(carrierRow(page, telcel)).toHaveCount(0);
});
