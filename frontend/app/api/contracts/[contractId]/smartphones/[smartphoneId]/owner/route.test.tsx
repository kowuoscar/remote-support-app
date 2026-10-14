import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PATCH } from "./route";

vi.mock("@/lib/api/backend", () => ({
  backendFetch: (path: string, init: RequestInit) => fetch(`http://backend${path}`, init),
}));

function correct(owner: string) {
  return PATCH(
    new NextRequest("http://localhost/api/contracts/c-1/smartphones/p-1/owner", {
      method: "PATCH",
      body: JSON.stringify({ owner }),
    }),
    { params: Promise.resolve({ contractId: "c-1", smartphoneId: "p-1" }) },
  );
}

describe("smartphone owner route", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("passes the backend's status and body through", async () => {
    const fetchMock = vi.fn(async () => Response.json({ id: "p-1", owner: "CLIENT" }, { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await correct("CLIENT");

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ id: "p-1", owner: "CLIENT" });
    expect(fetchMock).toHaveBeenCalledWith(
      "http://backend/api/contracts/c-1/smartphones/p-1/owner",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ owner: "CLIENT" }) }),
    );
  });

  it("passes the open-Return conflict and its coded body through", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ code: "SMARTPHONE_ON_OPEN_RETURN", message: "open" }, { status: 409 })),
    );

    const response = await correct("COMPANY");

    expect(response.status).toBe(409);
    expect(await response.json()).toEqual({ code: "SMARTPHONE_ON_OPEN_RETURN", message: "open" });
  });
});
