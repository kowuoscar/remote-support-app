import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { CarrierCatalog, CarrierOfferItem, CatalogCarrierItem } from "@/lib/api/types";
import { stubFetch } from "@/tests/component/fetch";
import { CarriersView } from "./carriers-view";

function offer(carrierId: string, name: string, price: number, archivedAt: string | null = null): CarrierOfferItem {
  return { id: `${carrierId}-${name}`, carrierId, name, price, archivedAt };
}

function carrier(id: string, name: string, overrides: Partial<CatalogCarrierItem> = {}): CatalogCarrierItem {
  return {
    id,
    country: "UNITED_STATES",
    name,
    archivedAt: null,
    topupOptions: [],
    postpaidPlans: [],
    ...overrides,
  };
}

// The server's order: active first, then by name, so the archived Carrier is last.
const VERIZON = carrier("c-verizon", "Verizon", {
  topupOptions: [offer("c-verizon", "Refill 20", 20), offer("c-verizon", "Refill 5", 5, "2026-08-01T00:00:00Z")],
  postpaidPlans: [offer("c-verizon", "Unlimited Plus", 55), offer("c-verizon", "Old Plan", 30, "2026-07-01T00:00:00Z")],
});
const TMOBILE = carrier("c-tmobile", "T-Mobile", {
  topupOptions: [offer("c-tmobile", "Magenta 10", 10)],
  postpaidPlans: [offer("c-tmobile", "Essentials", 45)],
});
const SPRINT = carrier("c-sprint", "Sprint", {
  archivedAt: "2026-06-15T00:00:00Z",
  topupOptions: [offer("c-sprint", "Sprint Refill", 15)],
  postpaidPlans: [offer("c-sprint", "Sprint Unlimited", 40)],
});

const CATALOG: CarrierCatalog = {
  country: "UNITED_STATES",
  currency: "USD",
  carriers: [VERIZON, TMOBILE, SPRINT],
};

function landmark() {
  return screen.getByRole("navigation", { name: "Carriers" });
}

function renderView(props: { catalog?: CarrierCatalog | null; initialCarrierId?: string } = {}) {
  return render(
    <CarriersView
      country="UNITED_STATES"
      catalog={props.catalog === undefined ? CATALOG : props.catalog}
      initialCarrierId={props.initialCarrierId}
    />,
  );
}

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

afterEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("CarriersView side list", () => {
  it("lists every Carrier in a Carriers landmark, archived last with Archived in its name", () => {
    renderView();
    const links = within(landmark()).getAllByRole("link");
    expect(links.map((link) => link.textContent?.replace(/\s+/g, " ").trim())).toEqual([
      "Verizon",
      "T-Mobile",
      "Sprint Archived",
    ]);
    expect(within(landmark()).getByRole("link", { name: /Sprint\s+Archived$/ })).toBeInTheDocument();
  });

  it("opens on the first Carrier, current, with its offers in place", () => {
    renderView();
    expect(within(landmark()).getByRole("link", { name: "Verizon" })).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("heading", { name: "Verizon" })).toBeInTheDocument();
    expect(screen.getByRole("list", { name: "Verizon topup options" })).toBeInTheDocument();
    expect(screen.getByRole("list", { name: "Verizon postpaid plans" })).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "T-Mobile postpaid plans" })).not.toBeInTheDocument();
  });

  it("a click shows that Carrier's lists with no request and writes ?carrier=", async () => {
    const fetchMock = stubFetch(200, {});
    renderView();

    await userEvent.click(within(landmark()).getByRole("link", { name: "T-Mobile" }));

    expect(within(landmark()).getByRole("link", { name: "T-Mobile" })).toHaveAttribute("aria-current", "true");
    expect(within(landmark()).getByRole("link", { name: "Verizon" })).not.toHaveAttribute("aria-current");
    expect(screen.getByRole("list", { name: "T-Mobile postpaid plans" })).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "Verizon postpaid plans" })).not.toBeInTheDocument();
    expect(window.location.search).toBe("?carrier=c-tmobile");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("opens the Carrier the address names, and the first row for an unknown one", () => {
    const { unmount } = renderView({ initialCarrierId: "c-tmobile" });
    expect(within(landmark()).getByRole("link", { name: "T-Mobile" })).toHaveAttribute("aria-current", "true");
    unmount();

    renderView({ initialCarrierId: "nonsense" });
    expect(within(landmark()).getByRole("link", { name: "Verizon" })).toHaveAttribute("aria-current", "true");
  });

  it("every row is a real link to its Carrier", () => {
    renderView();
    expect(within(landmark()).getByRole("link", { name: "T-Mobile" })).toHaveAttribute("href", "/?carrier=c-tmobile");
  });
});

describe("CarriersView detail", () => {
  it("an archived Carrier shows its name, when it was archived and its lists with no actions", async () => {
    renderView({ initialCarrierId: "c-sprint" });

    expect(screen.getByRole("heading", { name: "Sprint" })).toBeInTheDocument();
    expect(screen.getByText("Archived Jun 15, 2026")).toBeInTheDocument();
    const topups = screen.getByRole("list", { name: "Sprint topup options" });
    const plans = screen.getByRole("list", { name: "Sprint postpaid plans" });
    expect(within(topups).getByText("Sprint Refill")).toBeInTheDocument();
    expect(within(plans).getByText("Sprint Unlimited")).toBeInTheDocument();
    expect(screen.queryAllByRole("button", { name: /^(Add a |Edit |Rename |Archive )/ })).toHaveLength(0);
  });

  it("a live Carrier keeps Rename, Archive and each offer's actions", () => {
    renderView();
    expect(screen.getByRole("button", { name: "Rename Verizon" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Archive Verizon" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add a postpaid plan to Verizon" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit Unlimited Plus" })).toBeInTheDocument();
  });

  it("Show archived counts and reveals only the selected Carrier's archived entries", async () => {
    renderView();
    const toggle = screen.getByRole("checkbox", { name: /Show archived/ });
    // Verizon holds one archived Topup Option and one archived Plan; the archived Sprint is not counted.
    expect(toggle).toHaveAccessibleName(/^Show archived\s*\(2\)$/);
    expect(screen.queryByText("Old Plan")).not.toBeInTheDocument();

    await userEvent.click(toggle);

    expect(screen.getByText("Old Plan")).toBeInTheDocument();
    expect(screen.getByText("Refill 5")).toBeInTheDocument();

    await userEvent.click(within(landmark()).getByRole("link", { name: "T-Mobile" }));
    expect(screen.getByRole("checkbox", { name: /Show archived/ })).toHaveAccessibleName(/^Show archived$/);
  });
});

describe("CarriersView empty and failed", () => {
  it("a Country with no Carriers says so across the detail, with no landmark and Add carrier reachable", () => {
    renderView({ catalog: { ...CATALOG, carriers: [] } });
    expect(screen.getByText("No carriers in United States yet")).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Carriers" })).not.toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Add carrier" }).length).toBeGreaterThan(0);
  });

  it("a failed catalog says so, with no landmark", () => {
    renderView({ catalog: null });
    expect(screen.getByText("Couldn't load carriers")).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Carriers" })).not.toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Add carrier" })[0]).toBeDisabled();
  });
});
