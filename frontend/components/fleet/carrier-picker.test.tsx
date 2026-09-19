import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { CarrierItem } from "@/lib/api/types";
import { CarrierPicker } from "./carrier-picker";

function carrier(name: string, archivedAt: string | null = null): CarrierItem {
  return { id: `id-${name}`, country: "UNITED_STATES", name, archivedAt };
}

describe("CarrierPicker", () => {
  it("offers only active Carriers, and requires a choice", () => {
    render(
      <CarrierPicker
        name="carrierId"
        carriers={[carrier("Verizon"), carrier("Sprint", "2024-04-01T00:00:00Z"), carrier("AT&T")]}
        carriersHref="/agent/carriers"
      />,
    );

    const picker = screen.getByRole("combobox", { name: "Carrier" });
    expect(picker).toBeRequired();
    const options = screen.getAllByRole("option").map((option) => option.textContent);
    expect(options).toEqual(["Choose a carrier", "AT&T", "Verizon"]);
  });

  it("points to the Carriers page when the Country has no active Carrier", () => {
    render(
      <CarrierPicker
        name="carrierId"
        carriers={[carrier("Sprint", "2024-04-01T00:00:00Z")]}
        carriersHref="/agent/carriers"
      />,
    );

    expect(screen.queryByRole("combobox", { name: "Carrier" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Add one on the Carriers page" })).toHaveAttribute(
      "href",
      "/agent/carriers",
    );
  });
});
