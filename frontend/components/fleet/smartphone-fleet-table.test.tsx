import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { SmartphoneListItem } from "@/lib/api/types";
import { SmartphoneFleetTable } from "./smartphone-fleet-table";

const phone: SmartphoneListItem = {
  id: "phone-1",
  model: "Pixel 8",
  serial: "SN-1",
  owner: "COMPANY",
  status: "ACTIVE",
} as SmartphoneListItem;

describe("SmartphoneFleetTable", () => {
  it("renders the model control with canCorrect and plain text without it", () => {
    const { rerender } = render(
      <SmartphoneFleetTable phones={[phone]} simCards={[]} contractId="contract-1" canCorrect />,
    );
    expect(screen.getByRole("button", { name: "Edit model, Pixel 8" })).toBeInTheDocument();

    rerender(<SmartphoneFleetTable phones={[phone]} simCards={[]} contractId="contract-1" />);
    expect(screen.getByText("Pixel 8")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Edit/ })).not.toBeInTheDocument();
  });

  it("lets canEditSerial alone govern the serial control", () => {
    render(<SmartphoneFleetTable phones={[phone]} simCards={[]} contractId="contract-1" canEditSerial />);

    // The serial control is the only Edit button: the model stays plain text.
    expect(screen.getAllByRole("button", { name: /^Edit/ })).toHaveLength(1);
    expect(screen.getByText("Pixel 8")).toBeInTheDocument();
    expect(screen.getByText("SN-1")).toBeInTheDocument();
  });

  it("shows no serial control for a canCorrect-only table", () => {
    render(<SmartphoneFleetTable phones={[phone]} simCards={[]} contractId="contract-1" canCorrect />);

    // The model and owner controls offer Edit; the serial is plain text.
    expect(screen.getAllByRole("button", { name: /^Edit/ }).map((b) => b.getAttribute("aria-label"))).toEqual([
      "Edit model, Pixel 8",
      "Edit owner of Pixel 8",
    ]);
    expect(screen.getByText("SN-1")).toBeInTheDocument();
  });

  it("renders the owner control with canCorrect and plain text without it", () => {
    const { rerender } = render(
      <SmartphoneFleetTable phones={[phone]} simCards={[]} contractId="contract-1" canCorrect />,
    );
    expect(screen.getByRole("button", { name: "Edit owner of Pixel 8" })).toBeInTheDocument();

    rerender(<SmartphoneFleetTable phones={[phone]} simCards={[]} contractId="contract-1" />);
    expect(screen.getByText("Company")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit owner of Pixel 8" })).not.toBeInTheDocument();
  });
});
