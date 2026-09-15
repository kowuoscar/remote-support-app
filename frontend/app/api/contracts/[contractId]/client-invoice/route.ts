import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for a Contract's current-month Client Invoice draft (client-invoice-generation
 * ticket). GET creates the draft on first access as a side effect — see
 * ClientInvoiceController's Javadoc for why — so this proxy is a plain pass-through, the same
 * shape as app/api/contracts/[contractId]/fees/route.ts.
 */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/client-invoice`);
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
