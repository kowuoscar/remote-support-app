import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for Client creation: the browser never talks to the backend directly (same reasoning
 * as `app/api/session/route.ts`), it hits this same-origin route, which forwards the session
 * cookie as a Bearer token. The creation dialog (a Client Component) posts here, then calls
 * `router.refresh()` so the Server Component list re-fetches real data.
 */
export async function POST(request: NextRequest) {
  const body = await request.text();
  const backendResponse = await backendFetch("/api/clients", { method: "POST", body });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
