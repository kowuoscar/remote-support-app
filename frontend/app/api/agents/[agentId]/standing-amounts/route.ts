import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for a Manager viewing/updating an Agent's standing salary and standing Rollout
 * Advance (agent-standing-amounts-and-invoice-generation ticket). Plain pass-through, same shape
 * as every other proxy in this app.
 */
export async function GET(_request: NextRequest, { params }: { params: Promise<{ agentId: string }> }) {
  const { agentId } = await params;
  const backendResponse = await backendFetch(`/api/agents/${agentId}/standing-amounts`);
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}

export async function POST(request: NextRequest, { params }: { params: Promise<{ agentId: string }> }) {
  const { agentId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/agents/${agentId}/standing-amounts`, {
    method: "POST",
    body,
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
