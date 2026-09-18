import { NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { isCarrierOfferList } from "@/lib/api/carrier-offers";

/** BFF proxy for archiving a Topup Option or a Postpaid Plan. */
export async function POST(
  _request: Request,
  { params }: { params: Promise<{ carrierId: string; list: string; entryId: string }> },
) {
  const { carrierId, list, entryId } = await params;
  if (!isCarrierOfferList(list)) return new NextResponse(null, { status: 404 });
  const backendResponse = await backendFetch(`/api/carriers/${carrierId}/${list}/${entryId}/archive`, {
    method: "POST",
  });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
