import { vi } from "vitest";

function jsonResponse(status: number, body?: unknown) {
  return body === undefined
    ? new Response(null, { status })
    : new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

/**
 * Replaces the global `fetch` with a mock whose response the test settles by hand, so the
 * in-flight (pending) state can be asserted before the response arrives. `body`, when given, is
 * sent as JSON.
 */
export function stubPendingFetch() {
  let settle!: (response: Response) => void;
  const fetchMock = vi.fn(() => new Promise<Response>((resolve) => (settle = resolve)));
  vi.stubGlobal("fetch", fetchMock);
  return {
    fetchMock,
    respond: (status: number, body?: unknown) => settle(jsonResponse(status, body)),
  };
}

/** Replaces the global `fetch` with a mock that answers every request with `status` (and `body` as JSON). */
export function stubFetch(status: number, body?: unknown) {
  const fetchMock = vi.fn(async () => jsonResponse(status, body));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}
