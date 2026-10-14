import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PATCH } from "./route";

vi.mock("@/lib/api/backend", () => ({
  backendFetch: (path: string, init: RequestInit) => fetch(`http://backend${path}`, init),
}));

function correct(model: string) {
  return PATCH(
    new NextRequest("http://localhost/api/contracts/c-1/smartphones/p-1/model", {
      method: "PATCH",
      body: JSON.stringify({ model }),
    }),
    { params: Promise.resolve({ contractId: "c-1", smartphoneId: "p-1" }) },
  );
}

describe("smartphone model route", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("passes the backend's status and body through", async () => {
    const fetchMock = vi.fn(async () => Response.json({ id: "p-1", model: "Pixel 8" }, { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await correct("Pixel 8");

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ id: "p-1", model: "Pixel 8" });
    expect(fetchMock).toHaveBeenCalledWith(
      "http://backend/api/contracts/c-1/smartphones/p-1/model",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ model: "Pixel 8" }) }),
    );
  });

  it("passes an error status and its coded body through", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ code: "MODEL_TOO_LONG", message: "too long" }, { status: 400 })),
    );

    const response = await correct("x");

    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({ code: "MODEL_TOO_LONG", message: "too long" });
  });
});
