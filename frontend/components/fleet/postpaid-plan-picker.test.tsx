import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { CarrierOfferItem, CatalogCarrierItem } from "@/lib/api/types";

import { PostpaidPlanPicker } from "./postpaid-plan-picker";

function plan(name: string, price: number, archivedAt: string | null = null): CarrierOfferItem {
  return { id: `plan-${name}`, carrierId: "carrier-1", name, price, archivedAt };
}

function carrier(postpaidPlans: CarrierOfferItem[]): CatalogCarrierItem {
  return {
    id: "carrier-1",
    country: "UNITED_STATES",
    name: "Verizon",
    archivedAt: null,
    topupOptions: [],
    postpaidPlans,
  };
}

describe("PostpaidPlanPicker", () => {
  it("offers only the chosen Carrier's active Plans, and requires a choice", () => {
    render(
      <PostpaidPlanPicker
        name="postpaidPlanId"
        carrier={carrier([
          plan("Unlimited Plus", 80),
          plan("Start Unlimited", 70, "2024-06-01T00:00:00Z"),
          plan("Unlimited Welcome", 65),
        ])}
        currency="USD"
        carriersHref="/agent/carriers"
      />,
    );

    const picker = screen.getByRole("combobox", { name: "Postpaid plan" });
    expect(picker).toBeRequired();
    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual([
      "Choose a plan",
      "Unlimited Welcome",
      "Unlimited Plus",
    ]);
  });

  it("shows the monthly fee the chosen Plan sets", () => {
    render(
      <PostpaidPlanPicker
        carrier={carrier([plan("Unlimited Welcome", 65)])}
        currency="USD"
        carriersHref="/agent/carriers"
        value="plan-Unlimited Welcome"
        onChange={() => {}}
      />,
    );

    expect(screen.getByText("Monthly fee")).toBeInTheDocument();
    expect(screen.getByText("$65.00")).toBeInTheDocument();
  });

  it("asks for a Carrier first when none is chosen", () => {
    render(<PostpaidPlanPicker carrier={undefined} currency="USD" carriersHref="/agent/carriers" />);

    expect(screen.queryByRole("combobox", { name: "Postpaid plan" })).not.toBeInTheDocument();
    expect(screen.getByText("Choose a carrier first.")).toBeInTheDocument();
  });

  it("points to the Carriers page when the Carrier has no active Plan", () => {
    render(
      <PostpaidPlanPicker
        carrier={carrier([plan("Start Unlimited", 70, "2024-06-01T00:00:00Z")])}
        currency="USD"
        carriersHref="/agent/carriers"
      />,
    );

    expect(screen.queryByRole("combobox", { name: "Postpaid plan" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Add one on the Carriers page" })).toHaveAttribute(
      "href",
      "/agent/carriers",
    );
  });
});
