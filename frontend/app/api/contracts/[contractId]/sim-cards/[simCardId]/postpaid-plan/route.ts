import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/** BFF proxy for a Manager or Tester correcting a Postpaid SIM Card's plan (and so its monthly fee). */
export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ contractId: string; simCardId: string }> },
) {
  const { contractId, simCardId } = await params;
  return forwardJson(
    await backendFetch(`/api/contracts/${contractId}/sim-cards/${simCardId}/postpaid-plan`, {
      method: "PATCH",
      body: await request.text(),
    }),
  );
}
