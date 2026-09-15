import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for Contract creation — see app/api/clients/route.ts for the pattern. */
export async function POST(request: NextRequest) {
  const body = await request.text();
  const backendResponse = await backendFetch("/api/contracts", { method: "POST", body });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
