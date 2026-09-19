import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

/**
 * Every component test gets a fake Next.js router: client components call `useRouter()` from
 * `next/navigation`, which throws outside the App Router. Tests read it through `mockRouter`
 * (tests/component/next-navigation.ts) to assert a refresh or navigation happened.
 */
vi.mock("next/navigation", async () => (await import("./next-navigation")).nextNavigationMock);

// jsdom has no modal <dialog> support; every dialog component test only needs open/close to
// flip `.open`, not the browser's own focus-trapping/backdrop behaviour. Applied globally rather
// than per test file (LogRequestDialog/LogFeeDialog/SubmitRequestDialog's test files each used to
// carry an identical `beforeAll` for this).
HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
  this.open = true;
};
HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
  this.open = false;
};

afterEach(() => {
  cleanup();
});
