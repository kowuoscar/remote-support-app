import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PATCH } from "./route";

vi.mock("@/lib/api/backend", () => ({
  backendFetch: (path: string, init: RequestInit) => fetch(`http://backend${path}`, init),
}));

function correct(number: string) {
  return PATCH(
    new NextRequest("http://localhost/api/contracts/c-1/sim-cards/s-1/number", {
      method: "PATCH",
      body: JSON.stringify({ number }),
    }),
    { params: Promise.resolve({ contractId: "c-1", simCardId: "s-1" }) },
  );
}

describe("SIM card number route", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("passes the backend's status and body through", async () => {
    const fetchMock = vi.fn(async () => Response.json({ id: "s-1", number: "+44 1" }, { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await correct("+44 1");

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ id: "s-1", number: "+44 1" });
    expect(fetchMock).toHaveBeenCalledWith(
      "http://backend/api/contracts/c-1/sim-cards/s-1/number",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ number: "+44 1" }) }),
    );
  });

  it("passes an error status and its coded body through", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ code: "NUMBER_TOO_LONG", message: "too long" }, { status: 400 })),
    );

    const response = await correct("x");

    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({ code: "NUMBER_TOO_LONG", message: "too long" });
  });
});
