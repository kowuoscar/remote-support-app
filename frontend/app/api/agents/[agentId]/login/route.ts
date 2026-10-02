import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardSecretJson } from "@/lib/api/forward-secret-json";

/**
 * BFF proxy for a Manager creating the login of an Agent that has none
 * (create-login-for-existing-agent ticket). Plain pass-through, same shape as every other proxy
 * in this app.
 */
export async function POST(request: NextRequest, { params }: { params: Promise<{ agentId: string }> }) {
  const { agentId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/agents/${agentId}/login`, {
    method: "POST",
    body,
  });
  return forwardSecretJson(backendResponse);
}
