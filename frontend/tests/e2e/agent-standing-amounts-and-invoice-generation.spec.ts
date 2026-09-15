import { test, expect, type Page, type Locator } from "@playwright/test";

/**
 * A Manager setting an Agent's standing salary/Rollout Advance, and the Agent's own monthly
 * invoice reflecting the amount currently in effect — never the value the Manager just scheduled,
 * which only ever lands on *next* month's invoice (agent-standing-amounts-and-invoice-generation
 * ticket, user stories 5-6, 25-26). Driven against a real backend + Postgres (see
 * playwright.e2e.config.ts), at the accessibility-tree level per spec.md's Testing decisions.
 * Mirrors client-invoice-submission-and-visibility.spec.ts's pattern/helpers.
 *
 * There's no way to actually cross a real month boundary inside a test run, so this proves the
 * next-month-effective rule the same way the backend HTTP-seam tests do: capture what the
 * standing amount and the Agent Invoice's Salary/Rollout Advance lines show *before* the Manager's
 * change, apply the change, then assert both are still exactly what they were — the change is
 * confirmed as scheduled (the "takes effect from <month>" UI copy), but nothing currently visible
 * moves.
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

async function logout(page: Page) {
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login/);
}

/** The money figure shown in a standing-amount field's "Currently in effect" row. */
function currentlyInEffectAmount(form: Locator) {
  return form.locator(".tnum").first();
}

test.describe("agent standing amounts and invoice generation", () => {
  test("a manager updates an agent's standing salary and Rollout Advance; the agent's current invoice is unaffected until next month", async ({
    page,
  }) => {
    // Baseline: the seeded Agent's own invoice, as it stands before any change this test makes.
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/my-invoice");
    const salaryLine = page.locator("dt", { hasText: "Salary" }).locator("xpath=following-sibling::dd[1]");
    const repaymentLine = page
      .locator("dt", { hasText: "Rollout Advance repayment" })
      .locator("xpath=following-sibling::dd[1]");
    const newAdvanceLine = page
      .locator("dt", { hasText: "Rollout Advance new" })
      .locator("xpath=following-sibling::dd[1]");
    await expect(salaryLine).toBeVisible();
    const originalSalaryText = await salaryLine.textContent();
    const originalRepaymentText = await repaymentLine.textContent();
    const originalNewAdvanceText = await newAdvanceLine.textContent();
    await logout(page);

    // The Manager opens this same Agent's detail page and updates both standing amounts.
    await login(page, SEEDED_USERS.manager.username, SEEDED_USERS.manager.password);
    await page.goto("/manager/agents");
    await page.getByRole("link", { name: "Jordan Ellis" }).click();
    await expect(page).toHaveURL(/\/manager\/agents\/.+/);
    await expect(page.getByText("Standing amounts")).toBeVisible();

    const forms = page.locator("form");
    const salaryForm = forms.nth(0);
    const advanceForm = forms.nth(1);
    const salaryInput = salaryForm.getByLabel(/New amount/);
    const advanceInput = advanceForm.getByLabel(/New amount/);

    const originalSalaryInEffect = await currentlyInEffectAmount(salaryForm).textContent();
    const originalSalaryValue = await salaryInput.inputValue();
    const newSalaryValue = (Number(originalSalaryValue) + 137).toFixed(2);

    await salaryInput.fill(newSalaryValue);
    const [salaryResponse] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes("/standing-amounts") && resp.request().method() === "POST",
      ),
      salaryForm.getByRole("button", { name: "Save" }).click(),
    ]);
    expect(salaryResponse.status()).toBe(201);
    // The next-month-effective rule, stated in the UI itself.
    await expect(salaryForm.getByText(/Scheduled — takes effect from/)).toBeVisible();
    // Nothing currently in effect moved — the change only lands on the next invoice cycle.
    await expect(currentlyInEffectAmount(salaryForm)).toHaveText(originalSalaryInEffect ?? "");

    const [advanceResponse] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes("/standing-amounts") && resp.request().method() === "POST",
      ),
      (async () => {
        await advanceInput.fill("742.50");
        await advanceForm.getByRole("button", { name: "Save" }).click();
      })(),
    ]);
    expect(advanceResponse.status()).toBe(201);
    await expect(advanceForm.getByText(/Scheduled — takes effect from/)).toBeVisible();

    await logout(page);

    // The Agent's own invoice for the month already in progress must be byte-for-byte unchanged
    // by either update — the whole point of "next-month-effective".
    await login(page, SEEDED_USERS.agent.username, SEEDED_USERS.agent.password);
    await page.goto("/agent/my-invoice");
    await expect(page.locator("dt", { hasText: "Salary" }).locator("xpath=following-sibling::dd[1]")).toHaveText(
      originalSalaryText ?? "",
    );
    await expect(
      page.locator("dt", { hasText: "Rollout Advance repayment" }).locator("xpath=following-sibling::dd[1]"),
    ).toHaveText(originalRepaymentText ?? "");
    await expect(
      page.locator("dt", { hasText: "Rollout Advance new" }).locator("xpath=following-sibling::dd[1]"),
    ).toHaveText(originalNewAdvanceText ?? "");
  });
});
