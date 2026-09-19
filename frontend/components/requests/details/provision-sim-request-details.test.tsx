import type { ComponentProps } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { CatalogCarrierItem, SmartphoneListItem } from "@/lib/api/types";
import { ProvisionSimRequestDetails } from "./provision-sim-request-details";

function carrier(
  id: string,
  postpaidPlans: { id: string; name: string; price: number; archivedAt: string | null }[],
): CatalogCarrierItem {
  return {
    id,
    country: "UNITED_STATES",
    name: `Carrier ${id}`,
    archivedAt: null,
    topupOptions: [],
    postpaidPlans: postpaidPlans.map((plan) => ({ ...plan, carrierId: id })),
  };
}

function smartphone(id: string, model: string): SmartphoneListItem {
  return { id, contractId: "contract-1", model, serial: null, owner: "COMPANY", status: "ACTIVE" };
}

function renderDetails(overrides: Partial<ComponentProps<typeof ProvisionSimRequestDetails>> = {}) {
  return render(
    <ProvisionSimRequestDetails
      smartphones={[]}
      simCards={[]}
      carriers={[]}
      carriersHref=""
      currency="USD"
      {...overrides}
    />,
  );
}

describe("ProvisionSimRequestDetails", () => {
  it("defaults to Prepaid, with no Postpaid Plan picker", () => {
    renderDetails({
      carriers: [carrier("carrier-a", [{ id: "plan-a", name: "Unlimited", price: 60, archivedAt: null }])],
    });

    expect(screen.getByRole("combobox", { name: "Flavor" })).toHaveValue("PREPAID");
    expect(screen.queryByText("Postpaid plan")).not.toBeInTheDocument();
  });

  it("shows the Postpaid Plan picker once Postpaid is chosen, following the chosen Carrier", async () => {
    const carrierA = carrier("carrier-a", [{ id: "plan-a", name: "Unlimited A", price: 60, archivedAt: null }]);
    const carrierB = carrier("carrier-b", [{ id: "plan-b", name: "Unlimited B", price: 70, archivedAt: null }]);
    renderDetails({ carriers: [carrierA, carrierB] });

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Carrier" }), "carrier-a");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Flavor" }), "POSTPAID");

    expect(screen.getByRole("option", { name: "Unlimited A" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Unlimited B" })).not.toBeInTheDocument();
  });

  it("resets the chosen Plan when a different Carrier is picked", async () => {
    const carrierA = carrier("carrier-a", [{ id: "plan-a", name: "Unlimited A", price: 60, archivedAt: null }]);
    const carrierB = carrier("carrier-b", [{ id: "plan-b", name: "Unlimited B", price: 70, archivedAt: null }]);
    renderDetails({ carriers: [carrierA, carrierB] });

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Carrier" }), "carrier-a");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Flavor" }), "POSTPAID");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Postpaid plan" }), "plan-a");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Carrier" }), "carrier-b");

    expect(screen.getByRole("combobox", { name: "Postpaid plan" })).toHaveValue("");
  });

  it("offers an optional target Smartphone, not required", () => {
    renderDetails({ smartphones: [smartphone("phone-1", "Pixel 8")] });

    const picker = screen.getByRole("combobox", { name: "Target Smartphone" });
    expect(picker).not.toBeRequired();
    expect(screen.getByRole("option", { name: "None" })).toBeInTheDocument();
  });
});
