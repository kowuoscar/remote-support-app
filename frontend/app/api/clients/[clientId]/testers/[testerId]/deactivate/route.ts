import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/** BFF proxy for a Manager deactivating a Tester's Login (deactivate-a-login spec). Plain pass-through. */
export async function POST(_request: Request, { params }: { params: Promise<{ clientId: string; testerId: string }> }) {
  const { clientId, testerId } = await params;
  return forwardJson(await backendFetch(`/api/clients/${clientId}/testers/${testerId}/deactivate`, { method: "POST" }));
}
