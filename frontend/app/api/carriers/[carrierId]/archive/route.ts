import { NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for archiving a Carrier — see app/api/clients/route.ts for the pattern. */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ carrierId: string }> },
) {
  const { carrierId } = await params;
  const backendResponse = await backendFetch(`/api/carriers/${carrierId}/archive`, { method: "POST" });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
