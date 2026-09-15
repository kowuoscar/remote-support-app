import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for sending a Contract's draft Client Invoice
 * (client-invoice-submission-and-visibility ticket AC: "Agent can send a draft Client Invoice").
 * Plain pass-through, same shape as every other proxy in this app — no body either way.
 */
export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/client-invoice/send`, {
    method: "POST",
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
