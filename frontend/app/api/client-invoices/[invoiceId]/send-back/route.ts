import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/** BFF proxy for a Manager sending a Client Invoice back to draft with a reason, by its own id (any billing month). */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string }> },
) {
  const { invoiceId } = await params;
  return forwardJson(
    await backendFetch(`/api/client-invoices/${invoiceId}/send-back`, {
      method: "POST",
      body: await request.text(),
    }),
  );
}
