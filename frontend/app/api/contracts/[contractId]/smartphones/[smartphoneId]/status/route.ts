import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/** BFF proxy for changing a Smartphone's status. */
export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ contractId: string; smartphoneId: string }> },
) {
  const { contractId, smartphoneId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(
    `/api/contracts/${contractId}/smartphones/${smartphoneId}/status`,
    { method: "PATCH", body },
  );
  return forwardJson(backendResponse);
}
