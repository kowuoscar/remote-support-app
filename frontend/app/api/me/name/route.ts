import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for `PUT /api/me/name` (manager-changes-own-name ticket) — plain pass-through, the
 * same shape as `app/api/me/password/route.ts`. Success is `204` with no body, hence
 * `responseBody || null`.
 */
export async function PUT(request: NextRequest) {
  const body = await request.text();
  const backendResponse = await backendFetch("/api/me/name", { method: "PUT", body });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody || null, {
    status: backendResponse.status,
    headers: responseBody ? { "Content-Type": "application/json" } : undefined,
  });
}
