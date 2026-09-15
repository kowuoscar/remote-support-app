import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for a Manager marking an approved Agent Invoice as paid
 * (agent-invoice-submission-and-approval ticket AC: "Manager can mark an approved Agent Invoice
 * as paid ... no payment is executed by the app"). Plain pass-through, same shape as every other
 * proxy in this app.
 */
export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ agentId: string }> },
) {
  const { agentId } = await params;
  const backendResponse = await backendFetch(`/api/agents/${agentId}/invoice/paid`, {
    method: "POST",
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
