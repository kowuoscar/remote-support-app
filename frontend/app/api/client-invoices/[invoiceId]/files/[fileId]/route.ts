import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardBinary } from "@/lib/api/forward-binary";

/** BFF proxy for downloading one of a Client Invoice's Carrier Invoice Files, by invoice id. */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string; fileId: string }> },
) {
  const { invoiceId, fileId } = await params;
  return forwardBinary(await backendFetch(`/api/client-invoices/${invoiceId}/files/${fileId}`));
}
