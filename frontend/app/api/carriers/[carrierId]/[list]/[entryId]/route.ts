import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { isCarrierOfferList } from "@/lib/api/carrier-offers";

/** BFF proxy for editing a Topup Option's or Postpaid Plan's name and price. */
export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ carrierId: string; list: string; entryId: string }> },
) {
  const { carrierId, list, entryId } = await params;
  if (!isCarrierOfferList(list)) return new NextResponse(null, { status: 404 });
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/carriers/${carrierId}/${list}/${entryId}`, {
    method: "PATCH",
    body,
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
