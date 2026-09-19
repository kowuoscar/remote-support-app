import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { CatalogCarrierItem, SimCardListItem } from "@/lib/api/types";
import { TopupRequestDetails } from "./topup-request-details";

function simCard(number: string, carrierId?: string): SimCardListItem {
  return {
    id: `id-${number}`,
    contractId: "contract-1",
    number,
    carrierId,
    carrierName: carrierId ? `Carrier ${carrierId}` : undefined,
    flavor: "PREPAID",
    monthlyFeeAmount: null,
    status: "ACTIVE",
  };
}

function carrierWithOptions(id: string, options: { id: string; name: string; price: number; archivedAt: string | null }[]): CatalogCarrierItem {
  return {
    id,
    country: "UNITED_STATES",
    name: `Carrier ${id}`,
    archivedAt: null,
    topupOptions: options.map((o) => ({ ...o, carrierId: id })),
    postpaidPlans: [],
  };
}

describe("TopupRequestDetails", () => {
  it("requires a SIM Card, and shows a Topup Option picker once one is chosen with an active Option", async () => {
    const carrierA = carrierWithOptions("carrier-a", [{ id: "opt-1", name: "Refill 25", price: 25, archivedAt: null }]);

    render(
      <TopupRequestDetails
        smartphones={[]}
        simCards={[simCard("+1-555-0100", "carrier-a")]}
        carriers={[carrierA]}
        carriersHref=""
        currency="USD"
      />,
    );

    expect(screen.getByRole("combobox", { name: "SIM Card to top up" })).toBeRequired();

    await userEvent.selectOptions(
      screen.getByRole("combobox", { name: "SIM Card to top up" }),
      screen.getByRole("option", { name: /\+1-555-0100/ }),
    );

    const optionPicker = screen.getByRole("combobox", { name: "Topup Option" });
    expect(optionPicker).toBeRequired();
    expect(screen.getAllByRole("option", { name: "Refill 25" })).toHaveLength(1);
    expect(screen.getByLabelText("Description (optional)")).not.toBeRequired();
  });

  it("follows the chosen SIM Card's own Carrier when a different SIM Card is picked", async () => {
    const carrierA = carrierWithOptions("carrier-a", [{ id: "opt-a", name: "Refill 25", price: 25, archivedAt: null }]);
    const carrierB = carrierWithOptions("carrier-b", [{ id: "opt-b", name: "Recharge 20", price: 20, archivedAt: null }]);

    render(
      <TopupRequestDetails
        smartphones={[]}
        simCards={[simCard("+1-555-0100", "carrier-a"), simCard("+44-7700-900000", "carrier-b")]}
        carriers={[carrierA, carrierB]}
        carriersHref=""
        currency="USD"
      />,
    );

    await userEvent.selectOptions(
      screen.getByRole("combobox", { name: "SIM Card to top up" }),
      screen.getByRole("option", { name: /\+1-555-0100/ }),
    );
    expect(screen.getByRole("option", { name: "Refill 25" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Recharge 20" })).not.toBeInTheDocument();

    await userEvent.selectOptions(
      screen.getByRole("combobox", { name: "SIM Card to top up" }),
      screen.getByRole("option", { name: /\+44-7700-900000/ }),
    );
    expect(screen.getByRole("option", { name: "Recharge 20" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Refill 25" })).not.toBeInTheDocument();
  });

  it("asks for a description instead of an Option when the SIM Card's Carrier has no active one", async () => {
    const carrierWithNoOptions = carrierWithOptions("carrier-c", []);

    render(
      <TopupRequestDetails
        smartphones={[]}
        simCards={[simCard("+1-555-0200", "carrier-c")]}
        carriers={[carrierWithNoOptions]}
        carriersHref=""
        currency="USD"
      />,
    );

    await userEvent.selectOptions(
      screen.getByRole("combobox", { name: "SIM Card to top up" }),
      screen.getByRole("option", { name: /\+1-555-0200/ }),
    );

    expect(screen.queryByRole("combobox", { name: "Topup Option" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Description")).toBeRequired();
  });

  it("ignores an archived Option when deciding whether the Carrier has an active one", async () => {
    const carrier = carrierWithOptions("carrier-d", [
      { id: "opt-old", name: "Old Refill", price: 10, archivedAt: "2024-01-01T00:00:00Z" },
    ]);

    render(
      <TopupRequestDetails
        smartphones={[]}
        simCards={[simCard("+1-555-0300", "carrier-d")]}
        carriers={[carrier]}
        carriersHref=""
        currency="USD"
      />,
    );

    await userEvent.selectOptions(
      screen.getByRole("combobox", { name: "SIM Card to top up" }),
      screen.getByRole("option", { name: /\+1-555-0300/ }),
    );

    expect(screen.queryByRole("combobox", { name: "Topup Option" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Description")).toBeRequired();
  });
});
