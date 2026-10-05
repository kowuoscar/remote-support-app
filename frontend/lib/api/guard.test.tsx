import { afterEach, describe, expect, it, vi } from "vitest";
import { stubFetch } from "@/tests/component/fetch";

vi.mock("next/headers", () => ({
  cookies: async () => ({ get: () => ({ value: "token" }) }),
}));
const redirect = vi.hoisted(() =>
  vi.fn((path: string) => {
    throw new Error(`NEXT_REDIRECT ${path}`);
  }),
);
vi.mock("next/navigation", () => ({ redirect, unstable_rethrow: () => {} }));

import { requireAgent, requireTester } from "./guard";

const guards = [
  ["requireAgent", requireAgent, "AGENT", "TESTER"],
  ["requireTester", requireTester, "TESTER", "AGENT"],
] as const;

describe.each(guards)("%s", (_name, guard, role, otherRole) => {
  afterEach(() => {
    vi.unstubAllGlobals();
    redirect.mockClear();
  });

  it("non-ok-me-redirects-to-login", async () => {
    stubFetch(401);

    await expect(guard()).rejects.toThrow("NEXT_REDIRECT /login");
  });

  it("wrong-role-redirects-to-login", async () => {
    stubFetch(200, { username: "x@example.com", role: otherRole });

    await expect(guard()).rejects.toThrow("NEXT_REDIRECT /login");
  });

  it("right-role-returns", async () => {
    stubFetch(200, { username: "x@example.com", role });

    await expect(guard()).resolves.toBeUndefined();
    expect(redirect).not.toHaveBeenCalled();
  });
});
