import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for an Agent sending their own current-month Agent Invoice draft
 * (agent-invoice-submission-and-approval ticket AC: "Agent can send their draft Agent Invoice").
 * Plain pass-through, same shape as app/api/contracts/[contractId]/client-invoice/send/route.ts.
 */
export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ agentId: string }> },
) {
  const { agentId } = await params;
  const backendResponse = await backendFetch(`/api/agents/${agentId}/invoice/send`, {
    method: "POST",
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
