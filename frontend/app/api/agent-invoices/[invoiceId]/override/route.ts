import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for a Manager overriding a sent Agent Invoice's Salary/new advance, by the invoice's own id. */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string }> },
) {
  const { invoiceId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/agent-invoices/${invoiceId}/override`, { method: "POST", body });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
