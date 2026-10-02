import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardSecretJson } from "@/lib/api/forward-secret-json";

/** BFF proxy for Tester creation under a Client — see app/api/clients/route.ts for the pattern. */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ clientId: string }> },
) {
  const { clientId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/clients/${clientId}/testers`, {
    method: "POST",
    body,
  });
  return forwardSecretJson(backendResponse);
}
