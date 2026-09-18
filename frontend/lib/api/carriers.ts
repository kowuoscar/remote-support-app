import { unstable_rethrow } from "next/navigation";
import { backendFetch } from "@/lib/api/backend";
import type { CarrierCatalog, CarrierItem, Country } from "@/lib/api/types";

/**
 * The active Carriers a new SIM Card may name (sim-card-carrier ticket), or `[]` when the catalog
 * can't be loaded — the Carrier picker then points to the Carriers page. With no `country`, an
 * Agent gets their own Country's.
 */
export async function loadActiveCarriers(country?: Country): Promise<CarrierItem[]> {
  const query = country ? `?${new URLSearchParams({ country })}` : "";
  try {
    const response = await backendFetch(`/api/carriers${query}`);
    if (!response.ok) {
      console.error(`Carriers: active list load failed with status ${response.status}`);
      return [];
    }
    return ((await response.json()) as CarrierCatalog).carriers;
  } catch (error) {
    unstable_rethrow(error);
    console.error("Carriers: active list load failed with no response", error);
    return [];
  }
}

/**
 * One Country's Carriers, archived ones included (the page hides them behind "Show archived"), or
 * `null` when the catalog can't be loaded — logged, never thrown, so the page renders its own
 * unavailable state. With no `country`, an Agent gets their own Country's catalog.
 */
export async function loadCarrierCatalog(country?: Country): Promise<CarrierCatalog | null> {
  const query = new URLSearchParams({ includeArchived: "true" });
  if (country) query.set("country", country);
  try {
    const response = await backendFetch(`/api/carriers?${query}`);
    if (!response.ok) {
      console.error(`Carriers: catalog load failed with status ${response.status}`);
      return null;
    }
    return (await response.json()) as CarrierCatalog;
  } catch (error) {
    // Next.js signals a request-time render by throwing from cookies(); that must reach Next.js.
    unstable_rethrow(error);
    console.error("Carriers: catalog load failed with no response", error);
    return null;
  }
}
