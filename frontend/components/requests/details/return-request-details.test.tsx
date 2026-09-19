import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { SimCardListItem, SmartphoneListItem } from "@/lib/api/types";
import { ReturnRequestDetails, parseReturnedUnitIds } from "./return-request-details";

function smartphone(model: string, status: SmartphoneListItem["status"] = "ACTIVE"): SmartphoneListItem {
  return { id: `phone-${model}`, contractId: "contract-1", model, serial: null, owner: "CLIENT", status };
}

function simCard(number: string, status: SimCardListItem["status"] = "ACTIVE"): SimCardListItem {
  return {
    id: `sim-${number}`,
    contractId: "contract-1",
    number,
    flavor: "PREPAID",
    monthlyFeeAmount: null,
    status,
  };
}

describe("ReturnRequestDetails", () => {
  it("offers only Active units, grouped by kind, in one required multi-select", () => {
    render(
      <ReturnRequestDetails
        smartphones={[smartphone("Pixel 9"), smartphone("Pixel 8", "RETIRED")]}
        simCards={[simCard("+1-555-0100"), simCard("+1-555-0101", "RETIRED")]}
        carriers={[]}
        carriersHref=""
        currency="USD"
      />,
    );

    const picker = screen.getByRole("listbox", { name: "Units to return" });
    expect(picker).toBeRequired();
    expect(picker).toHaveAttribute("multiple");
    const options = screen.getAllByRole("option").map((option) => option.textContent);
    expect(options).toEqual(["Pixel 9", "+1-555-0100"]);
  });

  it("explains there is nothing to pick when no unit is Active", () => {
    render(<ReturnRequestDetails smartphones={[]} simCards={[]} carriers={[]} carriersHref="" currency="USD" />);

    expect(screen.queryByRole("listbox", { name: "Units to return" })).not.toBeInTheDocument();
    expect(screen.getByText("No Active units on this Contract yet.")).toBeInTheDocument();
  });

  it("shows a description field that stays optional", () => {
    render(
      <ReturnRequestDetails
        smartphones={[smartphone("Pixel 9")]}
        simCards={[]}
        carriers={[]}
        carriersHref=""
        currency="USD"
      />,
    );

    expect(screen.getByLabelText("Description (optional)")).not.toBeRequired();
  });
});

describe("parseReturnedUnitIds", () => {
  it("splits selected values back into a Smartphone list and a SIM Card list", () => {
    expect(parseReturnedUnitIds(["SMARTPHONE:a", "SIM_CARD:b", "SMARTPHONE:c"])).toEqual({
      returnedSmartphoneIds: ["a", "c"],
      returnedSimCardIds: ["b"],
    });
  });

  it("returns two empty lists when nothing was selected", () => {
    expect(parseReturnedUnitIds([])).toEqual({ returnedSmartphoneIds: [], returnedSimCardIds: [] });
  });
});
