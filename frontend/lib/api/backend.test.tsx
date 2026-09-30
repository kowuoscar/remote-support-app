import { afterEach, describe, expect, it, vi } from "vitest";
import { stubFetch } from "@/tests/component/fetch";

vi.mock("next/headers", () => ({
  cookies: async () => ({ get: () => ({ value: "token" }) }),
}));
vi.mock("next/navigation", () => ({ unstable_rethrow: () => {} }));

import { backendFetchJsonOrNull } from "./backend";

describe("backendFetchJsonOrNull", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("returns the parsed body on success", async () => {
    stubFetch(200, { a: 1 });

    expect(await backendFetchJsonOrNull("/api/x", "X")).toEqual({ a: 1 });
  });

  it("returns null and logs the label and status on a non-OK response", async () => {
    stubFetch(500);
    const log = vi.spyOn(console, "error").mockImplementation(() => {});

    expect(await backendFetchJsonOrNull("/api/x", "X")).toBeNull();
    expect(log).toHaveBeenCalledWith("X: load failed with status 500");
  });

  it("returns null and logs when the request itself fails", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("down")));
    const log = vi.spyOn(console, "error").mockImplementation(() => {});

    expect(await backendFetchJsonOrNull("/api/x", "X")).toBeNull();
    expect(log).toHaveBeenCalledWith("X: load failed with no response", expect.any(Error));
  });
});
