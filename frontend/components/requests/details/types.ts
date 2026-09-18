import type { CatalogCarrierItem, SimCardListItem, SmartphoneListItem } from "@/lib/api/types";

/**
 * The shared props every per-type details component receives (reboot-and-topup-details ticket):
 * the Active units of the Contract currently chosen, plus its Country's Carrier catalog, exactly
 * the same inputs regardless of which type is showing. Each component renders its own fields
 * (using backend field names as `name`s, so the surrounding form's plain FormData submit picks
 * them up with no extra wiring) — including a `description` field where the type calls for one,
 * since the shared submit/log dialogs stop rendering their own generic one once a type has its own
 * registered component (see `registry.tsx`).
 */
export interface RequestDetailsProps {
  smartphones: SmartphoneListItem[];
  simCards: SimCardListItem[];
  carriers: CatalogCarrierItem[];
  carriersHref: string;
  /** The Contract's currency — for a type (Topup) that shows a chosen catalog entry's price. */
  currency: string;
  disabled?: boolean;
}
