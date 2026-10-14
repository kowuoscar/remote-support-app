import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { SimCardListItem } from "@/lib/api/types";
import { SimCardFleetTable } from "./sim-card-fleet-table";

const sim = {
  id: "sim-1",
  contractId: "contract-1",
  monthlyFeeAmount: null,
  number: "+44 7700 900100",
  flavor: "PREPAID",
  status: "ACTIVE",
  installedInSmartphoneId: undefined,
  installedInSmartphoneModel: "Pixel 8",
} as SimCardListItem;

describe("SimCardFleetTable", () => {
  it("renders the number control with canCorrect and plain text without it", () => {
    const { rerender } = render(<SimCardFleetTable sims={[sim]} contractId="contract-1" canCorrect />);
    expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();

    rerender(<SimCardFleetTable sims={[sim]} contractId="contract-1" />);
    expect(screen.getByText("+44 7700 900100")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
  });

  it("leaves Installed in plain text for a canCorrect table without smartphones (Tester)", () => {
    render(<SimCardFleetTable sims={[sim]} contractId="contract-1" canCorrect />);

    // Only the number control offers Edit; Installed in stays read-only text.
    expect(screen.getAllByRole("button", { name: "Edit" })).toHaveLength(1);
    expect(screen.getByText("Pixel 8")).toBeInTheDocument();
  });
});
