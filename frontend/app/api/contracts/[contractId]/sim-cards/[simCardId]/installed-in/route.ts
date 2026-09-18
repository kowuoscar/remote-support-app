import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for setting, moving or clearing a SIM Card's Installed-in Smartphone — see app/api/clients/route.ts for the pattern. */
export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ contractId: string; simCardId: string }> },
) {
  const { contractId, simCardId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(
    `/api/contracts/${contractId}/sim-cards/${simCardId}/installed-in`,
    { method: "PATCH", body },
  );
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
