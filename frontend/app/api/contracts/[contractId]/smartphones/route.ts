import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for adding a Smartphone to a Contract's Fleet — see app/api/clients/route.ts for the pattern. */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/smartphones`, {
    method: "POST",
    body,
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
