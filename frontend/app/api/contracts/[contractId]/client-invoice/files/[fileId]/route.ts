import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for downloading one carrier invoice file. Unlike every other proxy in this app, the
 * backend response here is binary (the file's own bytes, not JSON), so this forwards the body and
 * the backend's Content-Type/Content-Disposition headers as-is instead of re-wrapping as JSON.
 */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ contractId: string; fileId: string }> },
) {
  const { contractId, fileId } = await params;
  const backendResponse = await backendFetch(`/api/contracts/${contractId}/client-invoice/files/${fileId}`);
  const body = await backendResponse.arrayBuffer();

  const headers = new Headers();
  const contentType = backendResponse.headers.get("content-type");
  const contentDisposition = backendResponse.headers.get("content-disposition");
  if (contentType) headers.set("Content-Type", contentType);
  if (contentDisposition) headers.set("Content-Disposition", contentDisposition);

  return new NextResponse(body, { status: backendResponse.status, headers });
}
