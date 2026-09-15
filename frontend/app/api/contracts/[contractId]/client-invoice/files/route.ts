import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for a Client Invoice draft's carrier invoice files (client-invoice-generation ticket
 * AC: "Agent can attach one or more carrier invoice files to the draft Client Invoice"). POST
 * forwards the browser's multipart body — and its boundary-bearing Content-Type — unchanged;
 * backendFetch defaults every request to a JSON Content-Type, but `init.headers` is spread last
 * there, so passing it explicitly here overrides that default, the same override order every
 * other BFF route already relies on.
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const contentType = request.headers.get("content-type") ?? "";
  const body = await request.arrayBuffer();
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/client-invoice/files`, {
    method: "POST",
    body,
    headers: { "Content-Type": contentType },
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Lists attached carrier invoice files — the main GET already embeds these, this exists for the upload control's own refresh. */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/client-invoice/files`);
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
