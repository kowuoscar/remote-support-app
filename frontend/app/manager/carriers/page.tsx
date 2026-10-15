import { SurfacePage } from "@/components/app-shell/surface-page";
import { CarriersView } from "@/components/carriers/carriers-view";
import { loadCarrierCatalog } from "@/lib/api/carriers";
import { requireManager } from "@/lib/api/guard";
import { COUNTRIES, type Country } from "@/lib/api/types";

export const metadata = { title: "Carriers" };

function parseCountry(value: string | string[] | undefined): Country {
  const match = COUNTRIES.find((c) => c.value === value);
  return match ? match.value : COUNTRIES[0].value;
}

/**
 * Every Country's Carriers, one Country at a time (agent-maintains-carriers ticket). The Country
 * filter lives in the URL (`?country=`), defaulting to the first Country in the list.
 */
export default async function ManagerCarriersPage({
  searchParams,
}: {
  searchParams: Promise<{ country?: string | string[]; carrier?: string | string[] }>;
}) {
  await requireManager();
  const params = await searchParams;
  const country = parseCountry(params.country);
  const initialCarrierId = Array.isArray(params.carrier) ? params.carrier[0] : params.carrier;
  const catalog = await loadCarrierCatalog(country);

  return (
    <SurfacePage title="Carriers" subtitle="Every country's catalog" viewerLabel="Manager" bleed>
      {/* Keyed by Country: a new Country opens on its own first Carrier, not on a stale selection. */}
      <CarriersView
        key={country}
        country={country}
        catalog={catalog}
        countryFilter
        initialCarrierId={initialCarrierId}
      />
    </SurfacePage>
  );
}
