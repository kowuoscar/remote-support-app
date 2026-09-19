import type { CatalogCarrierItem, SimCardListItem, SmartphoneListItem } from "@/lib/api/types";

/**
 * The one-Smartphone/one-SIM-Card/one-Carrier Reboot+Topup fixture shared by
 * LogRequestDialog.test.tsx and SubmitRequestDialog.test.tsx's "Reboot and Topup details" suites
 * — both dialogs render the exact same registered per-type details components
 * (reboot-and-topup-details ticket), just fed the fixture in a differently-shaped prop
 * (flat vs by-Contract), so the fixture data itself is identical.
 */
export const REBOOT_TOPUP_SMARTPHONES: SmartphoneListItem[] = [
  { id: "phone-1", contractId: "contract-1", model: "Pixel 9", serial: "SN-1", owner: "COMPANY", status: "ACTIVE" },
];

export const REBOOT_TOPUP_SIM_CARDS: SimCardListItem[] = [
  {
    id: "sim-1",
    contractId: "contract-1",
    number: "+1-555-0100",
    carrierId: "carrier-1",
    carrierName: "AT&T",
    flavor: "PREPAID",
    monthlyFeeAmount: null,
    status: "ACTIVE",
  },
];

export const REBOOT_TOPUP_CARRIERS: CatalogCarrierItem[] = [
  {
    id: "carrier-1",
    country: "UNITED_STATES",
    name: "AT&T",
    archivedAt: null,
    topupOptions: [{ id: "option-1", carrierId: "carrier-1", name: "Refill 25", price: 25, archivedAt: null }],
    postpaidPlans: [],
  },
];
