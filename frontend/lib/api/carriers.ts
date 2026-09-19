import { unstable_rethrow } from "next/navigation";
import { backendFetch } from "@/lib/api/backend";
import type { CarrierCatalog, CatalogCarrierItem, Country } from "@/lib/api/types";

/**
 * A Contract's own active Carrier catalog (reboot-and-topup-details ticket): the Contract-scoped
 * read `GET /api/contracts/{contractId}/carriers`, open to anyone who can view that Contract —
 * unlike {@link loadActiveCarriers}/{@link loadCarrierCatalog}, which need a Country the caller
 * doesn't have (a Tester has none of their own). `[]` when the catalog can't be loaded.
 */
export async function loadContractCarrierCatalog(contractId: string): Promise<CatalogCarrierItem[]> {
  try {
    const response = await backendFetch(`/api/contracts/${contractId}/carriers`);
    if (!response.ok) {
      console.error(`Carriers: contract-scoped catalog load failed with status ${response.status}`);
      return [];
    }
    return ((await response.json()) as CarrierCatalog).carriers;
  } catch (error) {
    unstable_rethrow(error);
    console.error("Carriers: contract-scoped catalog load failed with no response", error);
    return [];
  }
}

/**
 * The active Carriers a new SIM Card may name, each with its Postpaid Plans (sim-card-carrier and
 * postpaid-sim-plan tickets), or `[]` when the catalog can't be loaded — the Carrier picker then
 * points to the Carriers page. With no `country`, an Agent gets their own Country's.
 */
export async function loadActiveCarriers(country?: Country): Promise<CatalogCarrierItem[]> {
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
