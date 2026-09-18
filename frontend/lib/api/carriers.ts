import { unstable_rethrow } from "next/navigation";
import { backendFetch } from "@/lib/api/backend";
import type { CarrierCatalog, Country } from "@/lib/api/types";

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
