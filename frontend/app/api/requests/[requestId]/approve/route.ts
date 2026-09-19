import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";

/**
 * BFF proxy for a Manager approving a Request by its own id (manager-approves-requests ticket).
 * Forwards the caller's own body through unchanged (empty for every type but a Return needing
 * Dispositions chosen — manager-decides-return-disposition ticket) — an empty body here still
 * reaches the backend as no content at all, exactly as before, since `RequestApprovalRequest` is
 * optional there.
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ requestId: string }> },
) {
  const { requestId } = await params;
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/requests/${requestId}/approve`, { method: "POST", body });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
