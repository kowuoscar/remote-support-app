import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

function signIn() {
  return POST(
    new NextRequest("http://localhost/api/session", {
      method: "POST",
      body: JSON.stringify({ username: "a@example.com", password: "pw" }),
    }),
  );
}

function backendAnswers(status: number, body: unknown) {
  vi.stubGlobal("fetch", vi.fn(async () => Response.json(body, { status })));
}

describe("session route sign-in failures", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("login-deactivated-code-gives-deactivated-message", async () => {
    backendAnswers(401, { code: "LOGIN_DEACTIVATED" });
    const response = await signIn();
    expect(response.status).toBe(401);
    expect(await response.json()).toEqual({
      error: "This login has been deactivated. Ask your Manager if you need access again.",
    });
  });

  it("other-401-gives-incorrect-email-or-password", async () => {
    backendAnswers(401, { code: "BAD_CREDENTIALS" });
    const response = await signIn();
    expect(response.status).toBe(401);
    expect(await response.json()).toEqual({ error: "Incorrect email or password." });
  });

  it("non-json-failure-gives-incorrect-email-or-password", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("boom", { status: 500 })));
    const response = await signIn();
    expect(await response.json()).toEqual({ error: "Incorrect email or password." });
  });
});
