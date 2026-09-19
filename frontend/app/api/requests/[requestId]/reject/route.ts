import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for a Manager rejecting a Request by its own id, with a required reason
 * (manager-approves-requests ticket) — forwards the caller's JSON body straight through, the same
 * shape the Contract-scoped Request status PATCH route already uses for its own body.
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ requestId: string }> },
) {
  const { requestId } = await params;
  const backendResponse = await backendFetch(`/api/requests/${requestId}/reject`, {
    method: "POST",
    body: await request.text(),
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
