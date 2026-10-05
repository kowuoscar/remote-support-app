import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/** BFF proxy for reading one Client Invoice by its own id (any billing month), for the Contract's own Agent. */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string }> },
) {
  const { invoiceId } = await params;
  return forwardJson(await backendFetch(`/api/client-invoices/${invoiceId}`));
}
