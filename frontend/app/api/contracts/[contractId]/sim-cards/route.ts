import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for adding a SIM Card to a Contract's Fleet — see app/api/clients/route.ts for the pattern. */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/sim-cards`, {
    method: "POST",
    body,
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}

/**
 * BFF proxy for listing a Contract's SIM Cards — see the GET handler in the sibling
 * smartphones/route.ts for why this exists alongside the server-side fetch path.
 */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/sim-cards`);
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
