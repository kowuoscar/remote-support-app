import { request, type FullConfig } from "@playwright/test";
import { SESSION_COOKIE_NAME } from "../../lib/auth/session";

/**
 * Warms the routes this suite's very first assertion depends on, once, before any spec file runs
 * (diagnosed flake: `agent-invoice-submission-and-approval.spec.ts` times out on its very first
 * assertion, `getByText("Draft")`, only when it is the first spec Playwright runs against a
 * just-booted stack — passes immediately on any rerun against the now-warm stack, and reproduces
 * identically on a fresh Postgres, so it isn't accumulated state).
 *
 * The `webServer.url` health check (`GET /login`, `GET /api/health`) only proves each process
 * answers HTTP at all. It does not prove the app is warm: a Next.js production server still
 * lazily loads/compiles each route's server chunk on that route's first hit (App Router pages are
 * per-route bundles, not one eagerly-loaded bundle), and the backend's Spring Security filter
 * chain, JPA metamodel and BCrypt verification all pay their own one-time JIT/lazy-init cost on
 * their first real request. Whichever spec Playwright happens to run first inherits that combined
 * tax inside its own test's timeout budget — here, a 5s `toBeVisible()` on the very first
 * assertion after a login + navigation, which is tight enough to lose the race under a cold JVM
 * or a machine under load (this repo's worktrees regularly run several agents' suites at once).
 *
 * Paying that one-time tax here — outside any test's own timeout — removes the flake at its root
 * instead of masking it with a longer per-assertion timeout (which would only buy the *current*
 * first spec more room, not fix the underlying "first hit to any route is slow" cause for
 * whichever spec is first next time the suite is reordered).
 */
export default async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0]?.use?.baseURL as string | undefined;
  if (!baseURL) return;

  const context = await request.newContext({ baseURL });
  try {
    // Warms the login proxy route and the backend's auth path (Spring Security filter chain,
    // BCrypt, JPA user lookup) — every spec's first action.
    const loginResponse = await context.post("/api/session", {
      data: { username: "agent@example.com", password: "AgentDemo123!" },
    });
    if (!loginResponse.ok()) return;

    const state = await context.storageState();
    const hasSessionCookie = state.cookies.some((cookie) => cookie.name === SESSION_COOKIE_NAME);
    if (!hasSessionCookie) return;

    // Warms the specific page the flaky spec hits first — a full SSR render that itself calls
    // back into the backend for data, exercising both sides' first-hit cost together. The
    // context's own cookie jar already carries the session cookie `/api/session` just set.
    await context.get("/agent/my-invoice");
  } catch {
    // Best-effort warm-up: if the stack isn't reachable yet, let the suite's own webServer
    // readiness check and first real test surface that failure instead of masking it here.
  } finally {
    await context.dispose();
  }
}
