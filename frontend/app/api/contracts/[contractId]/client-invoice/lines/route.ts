import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for an Agent's edit of one line of a draft Client Invoice (edit-client-invoice-lines
 * ticket). Plain pass-through, same shape as every other proxy in this app: the body goes to the
 * backend untouched, and its status and body (the whole recomputed invoice, or a 400 `message`)
 * come back untouched.
 */
export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/client-invoice/lines`, {
    method: "PUT",
    body: await request.text(),
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
