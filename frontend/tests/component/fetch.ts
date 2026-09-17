import { vi } from "vitest";

/**
 * Replaces the global `fetch` with a mock whose response the test settles by hand, so the
 * in-flight (pending) state can be asserted before the response arrives.
 */
export function stubPendingFetch() {
  let settle!: (response: Response) => void;
  const fetchMock = vi.fn(() => new Promise<Response>((resolve) => (settle = resolve)));
  vi.stubGlobal("fetch", fetchMock);
  return {
    fetchMock,
    respond: (status: number) => settle(new Response(null, { status })),
  };
}

/** Replaces the global `fetch` with a mock that answers every request with `status`. */
export function stubFetch(status: number) {
  const fetchMock = vi.fn(async () => new Response(null, { status }));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}
