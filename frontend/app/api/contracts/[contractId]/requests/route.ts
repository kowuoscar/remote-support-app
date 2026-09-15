import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for submitting a Request against a Contract — see app/api/clients/route.ts for the pattern. */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/requests`, {
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
 * BFF proxy for listing a Contract's Requests. Server Components fetch this data directly via
 * backendFetch (see app/client/requests/page.tsx and app/agent/requests/page.tsx), so this
 * exists for client-side callers — notably exercising the "an Agent can't see Requests on a
 * Contract that isn't theirs" boundary (tester-request-submission ticket) from the browser, the
 * same way fleet-management's Smartphone/SIM Card proxies do.
 */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/requests`);
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
