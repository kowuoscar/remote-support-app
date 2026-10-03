import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/** BFF proxy for a Manager deactivating an Agent's Login (deactivate-a-login spec). Plain pass-through. */
export async function POST(_request: Request, { params }: { params: Promise<{ agentId: string }> }) {
  const { agentId } = await params;
  return forwardJson(await backendFetch(`/api/agents/${agentId}/login/deactivate`, { method: "POST" }));
}
