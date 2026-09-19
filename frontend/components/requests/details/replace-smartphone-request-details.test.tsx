import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { SmartphoneListItem } from "@/lib/api/types";
import { ReplaceSmartphoneRequestDetails } from "./replace-smartphone-request-details";

function smartphone(model: string, serial: string | null, status: SmartphoneListItem["status"] = "ACTIVE"): SmartphoneListItem {
  return { id: `id-${model}`, contractId: "contract-1", model, serial, owner: "COMPANY", status };
}

describe("ReplaceSmartphoneRequestDetails", () => {
  it("offers only Active Smartphones, and requires a choice", () => {
    render(
      <ReplaceSmartphoneRequestDetails
        smartphones={[smartphone("Pixel 9", "SN-1"), smartphone("Pixel 8", "SN-2", "RETIRED")]}
        simCards={[]}
        carriers={[]}
        carriersHref=""
        currency="USD"
      />,
    );

    const picker = screen.getByRole("combobox", { name: "Smartphone to replace" });
    expect(picker).toBeRequired();
    const options = screen.getAllByRole("option").map((option) => option.textContent);
    expect(options).toEqual(["Choose a Smartphone", "Pixel 9 — SN-1"]);
  });

  it("shows an optional requested model field, defaulting to the same model when left blank", () => {
    render(
      <ReplaceSmartphoneRequestDetails
        smartphones={[smartphone("Pixel 9", "SN-1")]}
        simCards={[]}
        carriers={[]}
        carriersHref=""
        currency="USD"
      />,
    );

    const modelField = screen.getByLabelText(/Requested model/);
    expect(modelField).not.toBeRequired();
  });

  it("shows a description field that stays optional", () => {
    render(
      <ReplaceSmartphoneRequestDetails
        smartphones={[smartphone("Pixel 9", "SN-1")]}
        simCards={[]}
        carriers={[]}
        carriersHref=""
        currency="USD"
      />,
    );

    expect(screen.getByLabelText("Description (optional)")).not.toBeRequired();
  });

  it("explains there is nothing to pick when no Smartphone is Active", () => {
    render(
      <ReplaceSmartphoneRequestDetails smartphones={[]} simCards={[]} carriers={[]} carriersHref="" currency="USD" />,
    );

    expect(screen.queryByRole("combobox", { name: "Smartphone to replace" })).not.toBeInTheDocument();
    expect(screen.getByText("No Active Smartphones on this Contract yet.")).toBeInTheDocument();
  });
});
