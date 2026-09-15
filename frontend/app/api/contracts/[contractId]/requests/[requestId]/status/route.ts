import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for changing a Request's status — see app/api/clients/route.ts for the pattern. */
export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ contractId: string; requestId: string }> },
) {
  const { contractId, requestId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(
    `/api/contracts/${contractId}/requests/${requestId}/status`,
    { method: "PATCH", body },
  );
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
