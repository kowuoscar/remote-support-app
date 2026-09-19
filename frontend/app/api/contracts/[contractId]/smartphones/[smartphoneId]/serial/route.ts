import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for setting or changing a Smartphone's serial — see app/api/clients/route.ts for the pattern. */
export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ contractId: string; smartphoneId: string }> },
) {
  const { contractId, smartphoneId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(
    `/api/contracts/${contractId}/smartphones/${smartphoneId}/serial`,
    { method: "PATCH", body },
  );
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
