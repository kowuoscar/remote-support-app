import { NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for a Manager resetting an Agent's password (manager-resets-a-password spec). Plain
 * pass-through, same shape as every other proxy in this app; the 200 body carries the generated
 * password, so the response is never cacheable.
 */
export async function POST(_request: Request, { params }: { params: Promise<{ agentId: string }> }) {
  const { agentId } = await params;
  const backendResponse = await backendFetch(`/api/agents/${agentId}/login/password`, { method: "POST" });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}
