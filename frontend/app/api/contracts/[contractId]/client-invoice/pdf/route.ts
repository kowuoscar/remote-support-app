import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for the on-demand Client Invoice PDF (client-invoice-submission-and-visibility ticket
 * AC: "A Tester can generate a PDF of the Client Invoice on demand"). Binary response, like
 * app/api/contracts/[contractId]/client-invoice/files/[fileId]/route.ts — forwards the backend's
 * bytes and Content-Type/Content-Disposition headers as-is instead of re-wrapping as JSON.
 */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ contractId: string }> },
) {
  const { contractId } = await params;
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/client-invoice/pdf`);
  const body = await backendResponse.arrayBuffer();

  const headers = new Headers();
  const contentType = backendResponse.headers.get("content-type");
  const contentDisposition = backendResponse.headers.get("content-disposition");
  if (contentType) headers.set("Content-Type", contentType);
  if (contentDisposition) headers.set("Content-Disposition", contentDisposition);

  return new NextResponse(body, { status: backendResponse.status, headers });
}
