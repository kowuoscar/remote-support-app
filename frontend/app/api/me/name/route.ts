import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/**
 * BFF proxy for `PUT /api/me/name` (manager-changes-own-name ticket) — plain pass-through, the
 * same shape as `app/api/me/password/route.ts`. Success is `204` with no body, which
 * `forwardJson` passes through as such.
 */
export async function PUT(request: NextRequest) {
  const body = await request.text();
  return forwardJson(await backendFetch("/api/me/name", { method: "PUT", body }));
}
