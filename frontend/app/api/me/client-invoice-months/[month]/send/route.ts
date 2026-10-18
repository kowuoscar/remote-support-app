import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/**
 * BFF proxy for `POST /api/me/client-invoice-months/{YYYY-MM}/send` (agent-sends-the-month): the
 * Agent sends the whole month's Client Invoices together. The body (`{invoiceIds}`) and the coded
 * refusals (409 `CARRIER_INVOICES_MISSING`, `MONTH_CHANGED`, `MONTH_ALREADY_SENT`,
 * `NOT_THE_CURRENT_MONTH`) pass through; the backend validates the month.
 */
export async function POST(request: NextRequest, { params }: { params: Promise<{ month: string }> }) {
  const { month } = await params;
  const body = await request.text();
  return forwardJson(
    await backendFetch(`/api/me/client-invoice-months/${encodeURIComponent(month)}/send`, { method: "POST", body }),
  );
}
