import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/**
 * BFF proxy for the Agent attaching a Carrier Invoice File to a draft Client Invoice, by its own
 * id. Forwards the browser's multipart body and its boundary-bearing Content-Type unchanged
 * (`init.headers` is spread last in backendFetch, so it overrides the JSON default).
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string }> },
) {
  const { invoiceId } = await params;
  const contentType = request.headers.get("content-type") ?? "";
  return forwardJson(
    await backendFetch(`/api/client-invoices/${invoiceId}/files`, {
      method: "POST",
      body: await request.arrayBuffer(),
      headers: { "Content-Type": contentType },
    }),
  );
}
