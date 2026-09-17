import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for a Manager approving a Client Invoice by its own id (any billing month). */
export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string }> },
) {
  const { invoiceId } = await params;
  const backendResponse = await backendFetch(`/api/client-invoices/${invoiceId}/approve`, { method: "POST" });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
