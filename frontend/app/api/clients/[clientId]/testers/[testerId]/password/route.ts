import { NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for a Manager resetting a Tester's password (manager-resets-a-password spec). Plain
 * pass-through; the 200 body carries the generated password, so the response is never cacheable.
 */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ clientId: string; testerId: string }> },
) {
  const { clientId, testerId } = await params;
  const backendResponse = await backendFetch(`/api/clients/${clientId}/testers/${testerId}/password`, {
    method: "POST",
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}
