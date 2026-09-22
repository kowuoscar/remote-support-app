import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for `POST /api/me/password` (change-password-dialog ticket) — plain pass-through, the
 * same shape every other proxy in this app already has, so the JWT never leaves the httpOnly
 * cookie. Success is `204` with no body; `responseBody || null` is what lets that status pass
 * straight through `NextResponse` unchanged — the Fetch spec forbids attaching even an empty
 * string body to a 204/205/304 response, so passing `""` through unconditionally would throw.
 */
export async function POST(request: NextRequest) {
  const body = await request.text();
  const backendResponse = await backendFetch("/api/me/password", {
    method: "POST",
    body,
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody || null, {
    status: backendResponse.status,
    headers: responseBody ? { "Content-Type": "application/json" } : undefined,
  });
}
