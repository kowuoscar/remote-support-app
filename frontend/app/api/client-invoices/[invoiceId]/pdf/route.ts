import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardBinary } from "@/lib/api/forward-binary";

/** BFF proxy for a Client Invoice's on-demand PDF, addressed by the invoice's own id. */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string }> },
) {
  const { invoiceId } = await params;
  return forwardBinary(await backendFetch(`/api/client-invoices/${invoiceId}/pdf`));
}
