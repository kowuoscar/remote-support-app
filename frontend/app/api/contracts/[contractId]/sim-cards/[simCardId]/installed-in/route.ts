import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/** BFF proxy for setting, moving or clearing a SIM Card's Installed-in Smartphone. */
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
  return forwardJson(backendResponse);
}
