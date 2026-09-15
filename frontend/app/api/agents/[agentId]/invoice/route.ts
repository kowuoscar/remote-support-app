import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for an Agent's current-month Agent Invoice draft
 * (agent-standing-amounts-and-invoice-generation ticket). GET creates the draft on first access
 * as a side effect — see AgentInvoiceController's Javadoc — so this is a plain pass-through, the
 * same shape as app/api/contracts/[contractId]/client-invoice/route.ts.
 */
export async function GET(_request: NextRequest, { params }: { params: Promise<{ agentId: string }> }) {
  const { agentId } = await params;
  const backendResponse = await backendFetch(`/api/agents/${agentId}/invoice`);
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
