import { randomUUID } from "node:crypto";
import { request, type FullConfig } from "@playwright/test";
import { SESSION_COOKIE_NAME } from "../../lib/auth/session";

/** The seeded agent@example.com login resolves to this Agent (V5 migration). */
const SEEDED_AGENT_ID = "55555555-5555-5555-5555-555555555555";

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

  // Not part of the best-effort warm-up below: a refusal here must fail the run.
  await sendTheSeededAgentsMonth();

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

/**
 * Sends the seeded Agent's (Jordan's) month once, before any spec, through the backend's month
 * send (agent-sends-the-month): the server refuses a never-sent invoice's first send while the
 * Agent's month is not sent, and every spec sends the invoice of a Contract it has just created on
 * its own. The isolated e2e backend runs without the demo profile, so nothing else sends Jordan's
 * month. With the month sent, every spec's new Contract is a late one, sent through the unchanged
 * card Send.
 *
 * As the Manager it creates a Client with a Contract for the seeded Agent that has nothing to bill
 * (a month with no Contract cannot be sent), then as Jordan calls the month send. A database reused
 * from an earlier run answers 409 MONTH_ALREADY_SENT, which is the state wanted; any other refusal
 * throws, so the run fails loudly instead of failing every spec for an unclear reason.
 */
async function sendTheSeededAgentsMonth(): Promise<void> {
  const backend = backendBaseUrl();
  const managerToken = await backendLogin(backend, "manager@example.com", "ChangeMe123!");
  const agentToken = await backendLogin(backend, "agent@example.com", "AgentDemo123!");

  const client = await backendPost(backend, "/api/clients", managerToken, 201, {
    name: `Month Anchor ${randomUUID()}`,
  });
  await backendPost(backend, "/api/contracts", managerToken, 201, {
    clientId: client.id,
    agentId: SEEDED_AGENT_ID,
  });

  const now = new Date();
  const month = `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, "0")}`;
  const response = await fetch(`${backend}/api/me/client-invoice-months/${month}/send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${agentToken}` },
  });
  if (response.ok) return;
  const body = (await response.json().catch(() => ({}))) as { code?: string };
  if (response.status === 409 && body.code === "MONTH_ALREADY_SENT") return;
  throw new Error(
    `e2e global setup could not send the seeded Agent's month ${month}: ` +
      `${response.status} ${JSON.stringify(body)}`,
  );
}

/** The backend the isolated stack (or the developer stack) runs, read the way the config sets it. */
function backendBaseUrl(): string {
  if (process.env.E2E_ISOLATED_BACKEND_PORT) {
    return `http://127.0.0.1:${process.env.E2E_ISOLATED_BACKEND_PORT}`;
  }
  return process.env.BACKEND_URL ?? "http://127.0.0.1:8080";
}

async function backendLogin(backend: string, username: string, password: string): Promise<string> {
  const body = await backendPost(backend, "/api/auth/login", undefined, 200, { username, password });
  return body.token as string;
}

async function backendPost(
  backend: string,
  path: string,
  token: string | undefined,
  expectedStatus: number,
  payload: unknown,
): Promise<Record<string, unknown>> {
  const response = await fetch(`${backend}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
  });
  if (response.status !== expectedStatus) {
    throw new Error(
      `e2e global setup: POST ${path} answered ${response.status}, expected ${expectedStatus}`,
    );
  }
  return (await response.json()) as Record<string, unknown>;
}
