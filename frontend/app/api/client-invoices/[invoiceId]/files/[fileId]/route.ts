import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { forwardBinary } from "@/lib/api/forward-binary";
import { forwardJson } from "@/lib/api/forward-json";

/** BFF proxy for downloading one of a Client Invoice's Carrier Invoice Files, by invoice id. */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string; fileId: string }> },
) {
  const { invoiceId, fileId } = await params;
  return forwardBinary(await backendFetch(`/api/client-invoices/${invoiceId}/files/${fileId}`));
}

/** BFF proxy for the Agent removing one file from a draft Client Invoice open to them: a 204, or the backend's refusal as-is. */
export async function DELETE(
  _request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string; fileId: string }> },
) {
  const { invoiceId, fileId } = await params;
  return forwardJson(await backendFetch(`/api/client-invoices/${invoiceId}/files/${fileId}`, { method: "DELETE" }));
}
