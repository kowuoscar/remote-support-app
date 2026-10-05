import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/** BFF proxy for the Agent's list of Client Invoices the Manager sent back (any billing month, no amounts). */
export async function GET() {
  return forwardJson(await backendFetch("/api/client-invoices/sent-back"));
}
