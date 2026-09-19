import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/** BFF proxy for a Manager approving a Request by its own id (manager-approves-requests ticket). */
export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ requestId: string }> },
) {
  const { requestId } = await params;
  const backendResponse = await backendFetch(`/api/requests/${requestId}/approve`, { method: "POST" });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
