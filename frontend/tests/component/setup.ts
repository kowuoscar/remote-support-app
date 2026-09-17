import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

/**
 * Every component test gets a fake Next.js router: client components call `useRouter()` from
 * `next/navigation`, which throws outside the App Router. Tests read it through `mockRouter`
 * (tests/component/next-navigation.ts) to assert a refresh or navigation happened.
 */
vi.mock("next/navigation", async () => (await import("./next-navigation")).nextNavigationMock);

afterEach(() => {
  cleanup();
});
