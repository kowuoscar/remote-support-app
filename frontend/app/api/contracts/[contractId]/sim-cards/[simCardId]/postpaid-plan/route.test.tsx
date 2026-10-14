import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PATCH } from "./route";

vi.mock("@/lib/api/backend", () => ({
  backendFetch: (path: string, init: RequestInit) => fetch(`http://backend${path}`, init),
}));

function correct(postpaidPlanId: string) {
  return PATCH(
    new NextRequest("http://localhost/api/contracts/c-1/sim-cards/s-1/postpaid-plan", {
      method: "PATCH",
      body: JSON.stringify({ postpaidPlanId }),
    }),
    { params: Promise.resolve({ contractId: "c-1", simCardId: "s-1" }) },
  );
}

describe("SIM card postpaid-plan route", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("passes the backend's status and body through", async () => {
    const fetchMock = vi.fn(async () => Response.json({ id: "s-1", monthlyFeeAmount: 80 }, { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await correct("plan-1");

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ id: "s-1", monthlyFeeAmount: 80 });
    expect(fetchMock).toHaveBeenCalledWith(
      "http://backend/api/contracts/c-1/sim-cards/s-1/postpaid-plan",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ postpaidPlanId: "plan-1" }) }),
    );
  });

  it("passes an error status and its coded body through", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Response.json({ code: "PLAN_ARCHIVED", message: "archived" }, { status: 400 })),
    );

    const response = await correct("plan-1");

    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({ code: "PLAN_ARCHIVED", message: "archived" });
  });
});
