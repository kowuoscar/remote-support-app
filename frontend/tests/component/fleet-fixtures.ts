import type { SimCardListItem, SmartphoneListItem } from "@/lib/api/types";

export function contract(id: string, clientName: string, countryLabel: string) {
  return { id, label: `${clientName} — ${countryLabel}`, meta: countryLabel, currency: "USD", clientName, countryLabel };
}

export const CONTRACTS = [
  contract("c-zeta", "Zeta Mobile", "Kenya"),
  contract("c-acme-ke", "Acme", "Kenya"),
  contract("c-acme-ug", "Acme", "Uganda"),
];

const phone = (id: string, contractId: string, model: string) =>
  ({ id, contractId, model, serial: null, owner: "COMPANY", status: "ACTIVE" }) as SmartphoneListItem;
const sim = (id: string, contractId: string, number: string) =>
  ({
    id,
    contractId,
    number,
    flavor: "PREPAID",
    status: "ACTIVE",
    monthlyFeeAmount: null,
  }) as SimCardListItem;

export const PHONES = [phone("p1", "c-acme-ke", "Pixel 8"), phone("p2", "c-zeta", "Galaxy S24"), phone("p3", "c-zeta", "Moto G")];
export const SIMS = [sim("s1", "c-acme-ke", "+254 700 000001"), sim("s2", "c-zeta", "+254 700 000002")];
