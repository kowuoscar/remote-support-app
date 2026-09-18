import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { SimCardListItem } from "@/lib/api/types";
import { ReplaceSimRequestDetails } from "./replace-sim-request-details";

function simCard(number: string, status: SimCardListItem["status"] = "ACTIVE"): SimCardListItem {
  return { id: `id-${number}`, contractId: "contract-1", number, flavor: "PREPAID", monthlyFeeAmount: null, status };
}

describe("ReplaceSimRequestDetails", () => {
  it("offers only Active SIM Cards, and requires a choice", () => {
    render(
      <ReplaceSimRequestDetails
        smartphones={[]}
        simCards={[simCard("+1-555-0100"), simCard("+1-555-0200", "RETIRED")]}
        carriers={[]}
        carriersHref=""
        currency="USD"
      />,
    );

    const picker = screen.getByRole("combobox", { name: "SIM Card to replace" });
    expect(picker).toBeRequired();
    const options = screen.getAllByRole("option").map((option) => option.textContent);
    expect(options).toEqual(["Choose a SIM Card", "+1-555-0100"]);
  });

  it("shows a description field that stays optional", () => {
    render(
      <ReplaceSimRequestDetails
        smartphones={[]}
        simCards={[simCard("+1-555-0100")]}
        carriers={[]}
        carriersHref=""
        currency="USD"
      />,
    );

    expect(screen.getByLabelText("Description (optional)")).not.toBeRequired();
  });

  it("explains there is nothing to pick when no SIM Card is Active", () => {
    render(<ReplaceSimRequestDetails smartphones={[]} simCards={[]} carriers={[]} carriersHref="" currency="USD" />);

    expect(screen.queryByRole("combobox", { name: "SIM Card to replace" })).not.toBeInTheDocument();
    expect(screen.getByText("No Active SIM Cards on this Contract yet.")).toBeInTheDocument();
  });
});
