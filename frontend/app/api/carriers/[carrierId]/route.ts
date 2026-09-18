import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for renaming a Carrier — see app/api/clients/route.ts for the pattern. */
export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ carrierId: string }> },
) {
  const { carrierId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/carriers/${carrierId}`, { method: "PATCH", body });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
