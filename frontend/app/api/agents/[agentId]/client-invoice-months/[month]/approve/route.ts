import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/**
 * BFF proxy for `POST /api/agents/{agentId}/client-invoice-months/{YYYY-MM}/approve`
 * (manager-reviews-by-agent): the Manager approves the invoices left to review in an Agent's month
 * together. The body (`{invoiceIds}`) and the coded refusals (409 `MONTH_CHANGED`,
 * `NOTHING_TO_APPROVE`) pass through; the backend validates the Agent and the month.
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ agentId: string; month: string }> },
) {
  const { agentId, month } = await params;
  const body = await request.text();
  return forwardJson(
    await backendFetch(
      `/api/agents/${encodeURIComponent(agentId)}/client-invoice-months/${encodeURIComponent(month)}/approve`,
      { method: "POST", body },
    ),
  );
}
