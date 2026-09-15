import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for a Manager approving a sent Agent Invoice (agent-invoice-submission-and-approval
 * ticket AC: "Manager can approve a sent Agent Invoice, moving it to status approved"). Plain
 * pass-through, same shape as every other proxy in this app.
 */
export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ agentId: string }> },
) {
  const { agentId } = await params;
  const backendResponse = await backendFetch(`/api/agents/${agentId}/invoice/approve`, {
    method: "POST",
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
