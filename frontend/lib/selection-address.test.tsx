import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockRouter } from "@/tests/component/next-navigation";
import { refreshPage, selectionHref, useSelectionAddress } from "@/lib/selection-address";

beforeEach(() => {
  window.history.replaceState(null, "", "/agent/fleet?contract=c1");
  mockRouter.refresh.mockClear();
});
afterEach(() => {
  window.history.replaceState(null, "", "/");
  vi.useRealTimers();
});

describe("selectionHref", () => {
  it("builds a relative link from the given parameters, leaving out the null ones", () => {
    expect(selectionHref("/x", { contract: "c 1", tab: null })).toBe("/x?contract=c+1");
    expect(selectionHref("/x", { tab: null })).toBe("/x");
  });
});

describe("useSelectionAddress", () => {
  it("sets and removes parameters on the current address, with no router call when nothing is refreshing", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2030-01-01")); // far from any refresh an earlier test made
    const { result } = renderHook(() => useSelectionAddress());
    result.current({ tab: "smartphones" });
    expect(window.location.pathname + window.location.search).toBe("/agent/fleet?contract=c1&tab=smartphones");
    result.current({ contract: "c2", tab: null });
    expect(window.location.pathname + window.location.search).toBe("/agent/fleet?contract=c2");
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("asks again for a refresh that may be in flight, once Next has applied the write", () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(() => useSelectionAddress());
    refreshPage(mockRouter);
    expect(mockRouter.refresh).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(500);
    result.current({ tab: "smartphones" });
    // Not in the tick of the write: Next would lose it to the restore it is about to apply.
    expect(mockRouter.refresh).toHaveBeenCalledTimes(1);
    rerender(); // Next applied the restore: useSearchParams now shows the new address
    expect(mockRouter.refresh).toHaveBeenCalledTimes(2);
    rerender();
    expect(mockRouter.refresh).toHaveBeenCalledTimes(2);
  });

  it("does not refresh for a refresh long since landed", () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(() => useSelectionAddress());
    refreshPage(mockRouter);
    vi.advanceTimersByTime(60_000);
    result.current({ tab: "smartphones" });
    rerender();
    expect(mockRouter.refresh).toHaveBeenCalledTimes(1);
  });

  it("writes nothing when the address would not change", () => {
    vi.useFakeTimers();
    const replaceState = vi.spyOn(window.history, "replaceState");
    const { result, rerender } = renderHook(() => useSelectionAddress());
    refreshPage(mockRouter);
    result.current({ contract: "c1" });
    rerender();
    expect(replaceState).not.toHaveBeenCalled();
    expect(mockRouter.refresh).toHaveBeenCalledTimes(1);
    replaceState.mockRestore();
  });
});
