import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/** BFF proxy for the Agent sending a draft Client Invoice, by its own id. A past-month never-sent draft answers a coded 409. */
export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string }> },
) {
  const { invoiceId } = await params;
  return forwardJson(await backendFetch(`/api/client-invoices/${invoiceId}/send`, { method: "POST" }));
}
