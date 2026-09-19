import type { SimCardListItem } from "@/lib/api/types";

/**
 * The numbers of every SIM Card Installed in {@code smartphoneId}, for a Smartphone table's "SIM
 * Cards" column (sim-installed-in-smartphone ticket AC: "Every Fleet table shows ... per
 * Smartphone, its SIM Cards"). Derived from the same `simCards` list every Fleet view already
 * holds, rather than a separate backend field, since the view already has both lists in hand.
 */
export function simCardNumbersInstalledIn(simCards: SimCardListItem[], smartphoneId: string): string {
  const numbers = simCards
    .filter((sim) => sim.installedInSmartphoneId === smartphoneId)
    .map((sim) => sim.number);
  return numbers.length > 0 ? numbers.join(", ") : "—";
}
