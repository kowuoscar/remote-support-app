import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for a Manager overriding a sent Agent Invoice's Salary and/or Rollout Advance
 * (new-advance) line (agent-invoice-submission-and-approval ticket AC: "Manager can override the
 * Salary or Rollout Advance value on that one invoice at approval time"). Plain pass-through,
 * forwarding the JSON body unchanged — same shape as every other proxy in this app.
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ agentId: string }> },
) {
  const { agentId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/agents/${agentId}/invoice/override`, {
    method: "POST",
    body,
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
