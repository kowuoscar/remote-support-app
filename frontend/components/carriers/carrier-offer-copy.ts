import type { CarrierOfferList } from "@/lib/api/carrier-offers";

/** The words each offer list uses on the Carriers page and in its dialogs. */
export const OFFER_COPY: Record<
  CarrierOfferList,
  { heading: string; singular: string; priceLabel: string; perMonth: boolean; nameTakenCode: string; example: string }
> = {
  "topup-options": {
    heading: "Topup options",
    singular: "topup option",
    priceLabel: "Price",
    perMonth: false,
    nameTakenCode: "TOPUP_OPTION_NAME_TAKEN",
    example: "e.g. Prepaid Refill 25…",
  },
  "postpaid-plans": {
    heading: "Postpaid plans",
    singular: "postpaid plan",
    priceLabel: "Monthly price",
    perMonth: true,
    nameTakenCode: "POSTPAID_PLAN_NAME_TAKEN",
    example: "e.g. Unlimited Starter…",
  },
};
