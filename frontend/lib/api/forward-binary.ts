import { NextResponse } from "next/server";

/**
 * Forwards a binary backend response (a PDF or a Carrier Invoice File) as-is: its bytes and its
 * Content-Type/Content-Disposition headers, instead of re-wrapping it as JSON.
 */
export async function forwardBinary(backendResponse: Response): Promise<NextResponse> {
  const body = await backendResponse.arrayBuffer();
  const headers = new Headers();
  const contentType = backendResponse.headers.get("content-type");
  const contentDisposition = backendResponse.headers.get("content-disposition");
  if (contentType) headers.set("Content-Type", contentType);
  if (contentDisposition) headers.set("Content-Disposition", contentDisposition);
  return new NextResponse(body, { status: backendResponse.status, headers });
}
