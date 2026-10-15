import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/** BFF proxy for a Manager or Agent correcting a Smartphone's owner. */
export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ contractId: string; smartphoneId: string }> },
) {
  const { contractId, smartphoneId } = await params;
  return forwardJson(
    await backendFetch(`/api/contracts/${contractId}/smartphones/${smartphoneId}/owner`, {
      method: "PATCH",
      body: await request.text(),
    }),
  );
}
