import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PATCH } from "./route";

const cookieJar = vi.hoisted(() => ({ token: undefined as string | undefined }));
vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) =>
      name === "rs_session" && cookieJar.token ? { value: cookieJar.token } : undefined,
  }),
}));

const BODY = '{"serial":"SN1"}';

function callRoute() {
  return PATCH(
    new NextRequest("http://localhost/api/contracts/c1/smartphones/s1/serial", { method: "PATCH", body: BODY }),
    { params: Promise.resolve({ contractId: "c1", smartphoneId: "s1" }) },
  );
}

function backendAnswers(status: number, body: string | null) {
  const fetchMock = vi.fn(async () => new Response(body, { status }));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("smartphone serial route", () => {
  beforeEach(() => {
    cookieJar.token = "jwt-1";
  });
  afterEach(() => vi.unstubAllGlobals());

  it("sends the method, path, body and session to the backend route", async () => {
    const fetchMock = backendAnswers(200, "{}");
    await callRoute();
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toMatch(/\/api\/contracts\/c1\/smartphones\/s1\/serial$/);
    expect(init.method).toBe("PATCH");
    expect(init.body).toBe(BODY);
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer jwt-1");
  });

  it("returns a success status and JSON body unchanged", async () => {
    backendAnswers(200, '{"id":"s1","status":"OK"}');
    const response = await callRoute();
    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Type")).toBe("application/json");
    expect(await response.text()).toBe('{"id":"s1","status":"OK"}');
  });

  it("returns an error status and coded body unchanged", async () => {
    backendAnswers(400, '{"code":"INVALID","message":"no"}');
    const response = await callRoute();
    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({ code: "INVALID", message: "no" });
  });

  it("refuses a call with no session, sending no credentials", async () => {
    cookieJar.token = undefined;
    const fetchMock = backendAnswers(401, null);
    const response = await callRoute();
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect((init.headers as Record<string, string>).Authorization).toBeUndefined();
    expect(response.status).toBe(401);
  });
});
