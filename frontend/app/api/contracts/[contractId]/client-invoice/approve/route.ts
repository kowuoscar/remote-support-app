import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for a Manager approving a Contract's sent Client Invoice
 * (client-invoice-submission-and-visibility ticket AC: "Manager can approve it, moving it to
 * status approved"). Plain pass-through, same shape as every other proxy in this app.
 */
export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/client-invoice/approve`, {
    method: "POST",
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
