import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for adding a Smartphone to a Contract's Fleet — see app/api/clients/route.ts for the pattern. */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/smartphones`, {
    method: "POST",
    body,
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}

/**
 * BFF proxy for listing a Contract's Smartphones. Server Components fetch this data directly via
 * backendFetch (see app/agent/fleet/page.tsx), so this exists for client-side callers — notably
 * exercising the "an Agent/Tester can't view Fleet on a Contract that isn't theirs" boundary
 * (fleet-management ticket) from the browser, the same way it happens for real.
 */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/smartphones`);
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
