/**
 * A Carrier's two offer lists — Topup Options and Postpaid Plans — named by the URL segment the
 * backend nests them under (`/api/carriers/{carrierId}/{list}`). The BFF routes accept only these.
 */
export const CARRIER_OFFER_LISTS = ["topup-options", "postpaid-plans"] as const;

export type CarrierOfferList = (typeof CARRIER_OFFER_LISTS)[number];

export function isCarrierOfferList(value: string): value is CarrierOfferList {
  return (CARRIER_OFFER_LISTS as readonly string[]).includes(value);
}
