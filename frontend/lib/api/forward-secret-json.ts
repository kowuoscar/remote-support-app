import { NextResponse } from "next/server";

/**
 * Forwards a backend JSON response whose body carries a generated password (a Login's creation or
 * reset): status and body as-is, and never cacheable.
 */
export async function forwardSecretJson(backendResponse: Response): Promise<NextResponse> {
  const body = await backendResponse.text();
  return new NextResponse(body, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}
