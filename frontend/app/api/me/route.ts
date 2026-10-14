import { NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for `GET /api/me` (manager-changes-own-name ticket), so the browser-side
 * `ChangeNameDialog` can read the caller's current Manager name without the JWT leaving the
 * httpOnly cookie. A plain pass-through of status and body.
 */
export async function GET() {
  const backendResponse = await backendFetch("/api/me");
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody || null, {
    status: backendResponse.status,
    headers: responseBody ? { "Content-Type": "application/json" } : undefined,
  });
}
