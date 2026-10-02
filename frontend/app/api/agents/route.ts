import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardSecretJson } from "@/lib/api/forward-secret-json";

/** BFF proxy for Agent creation — see app/api/clients/route.ts for the pattern. */
export async function POST(request: NextRequest) {
  const body = await request.text();
  const backendResponse = await backendFetch("/api/agents", { method: "POST", body });
  return forwardSecretJson(backendResponse);
}
