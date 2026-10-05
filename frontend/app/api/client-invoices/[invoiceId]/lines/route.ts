import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/** BFF proxy for the Agent's edit of one line of a draft Client Invoice, by its own id. Body and response pass through untouched. */
export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string }> },
) {
  const { invoiceId } = await params;
  return forwardJson(
    await backendFetch(`/api/client-invoices/${invoiceId}/lines`, {
      method: "PUT",
      body: await request.text(),
    }),
  );
}
