import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/api/backend";
import { isCarrierOfferList } from "@/lib/api/carrier-offers";

/**
 * BFF proxy for adding a Topup Option or a Postpaid Plan to a Carrier — see
 * app/api/clients/route.ts for the pattern. `list` is `topup-options` or `postpaid-plans`.
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ carrierId: string; list: string }> },
) {
  const { carrierId, list } = await params;
  if (!isCarrierOfferList(list)) return new NextResponse(null, { status: 404 });
  const body = await request.text();
  const backendResponse = await backendFetch(`/api/carriers/${carrierId}/${list}`, { method: "POST", body });
  const responseBody = await backendResponse.text();
  return new NextResponse(responseBody, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
