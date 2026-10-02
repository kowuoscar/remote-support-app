import { backendFetch } from "@/lib/api/backend";
import { forwardSecretJson } from "@/lib/api/forward-secret-json";

/**
 * BFF proxy for a Manager resetting an Agent's password (manager-resets-a-password spec). Plain
 * pass-through, same shape as every other proxy in this app; the 200 body carries the generated
 * password, so the response is never cacheable.
 */
export async function POST(_request: Request, { params }: { params: Promise<{ agentId: string }> }) {
  const { agentId } = await params;
  const backendResponse = await backendFetch(`/api/agents/${agentId}/login/password`, { method: "POST" });
  return forwardSecretJson(backendResponse);
}
