import type { CatalogCarrierItem } from "@/lib/api/types";

/**
 * A Carrier with the given Postpaid Plans, for RequestStatusControl's and ReplaceSimCompletion's
 * completion-form tests — both build the same shape (an active Carrier, "Plan {id}" named
 * Postpaid Plans at a flat $10). Extracted from the identical copies.
 */
export function carrier(id: string, plans: { id: string; archivedAt: string | null }[] = []): CatalogCarrierItem {
  return {
    id,
    country: "UNITED_STATES",
    name: `Carrier ${id}`,
    archivedAt: null,
    topupOptions: [],
    postpaidPlans: plans.map((plan) => ({ ...plan, carrierId: id, name: `Plan ${plan.id}`, price: 10 })),
  };
}
