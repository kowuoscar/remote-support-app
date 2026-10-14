import { cleanup, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { CatalogCarrierItem, SimCardListItem } from "@/lib/api/types";
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

  describe("plan control", () => {
    const verizon = {
      id: "carrier-1",
      country: "UNITED_STATES",
      name: "Verizon",
      archivedAt: null,
      topupOptions: [],
      postpaidPlans: [{ id: "plan-1", carrierId: "carrier-1", name: "Welcome", price: 65, archivedAt: null }],
    } as CatalogCarrierItem;
    const postpaid = {
      ...sim,
      flavor: "POSTPAID",
      monthlyFeeAmount: 65,
      carrierId: "carrier-1",
      carrierName: "Verizon",
      postpaidPlanId: "plan-1",
      postpaidPlanName: "Welcome",
    } as SimCardListItem;

    function renderTable(sims: SimCardListItem[], carriers?: CatalogCarrierItem[], canCorrect = true) {
      render(
        <SimCardFleetTable
          sims={sims}
          contractId="contract-1"
          currency="USD"
          canCorrect={canCorrect}
          carriers={carriers}
          carriersHref="/manager/carriers"
        />,
      );
    }

    it("offers Edit plan on a Postpaid SIM whose Carrier is in the active catalog", () => {
      renderTable([postpaid], [verizon]);

      expect(screen.getByRole("button", { name: "Edit plan" })).toBeInTheDocument();
    });

    it("offers no plan control on a Prepaid SIM", () => {
      renderTable([sim], [verizon]);

      expect(screen.queryByRole("button", { name: "Edit plan" })).not.toBeInTheDocument();
    });

    it("offers no plan control without carriers, for an archived or absent Carrier, or without canCorrect", () => {
      renderTable([postpaid]);
      expect(screen.queryByRole("button", { name: "Edit plan" })).not.toBeInTheDocument();
      cleanup();

      renderTable([postpaid], [{ ...verizon, id: "carrier-9" }]);
      expect(screen.queryByRole("button", { name: "Edit plan" })).not.toBeInTheDocument();
      cleanup();

      renderTable([postpaid], [{ ...verizon, archivedAt: "2024-01-01T00:00:00Z" }]);
      expect(screen.queryByRole("button", { name: "Edit plan" })).not.toBeInTheDocument();
      cleanup();

      renderTable([{ ...postpaid, carrierId: undefined }], [verizon]);
      expect(screen.queryByRole("button", { name: "Edit plan" })).not.toBeInTheDocument();
      cleanup();

      renderTable([postpaid], [verizon], false);
      expect(screen.queryByRole("button", { name: "Edit plan" })).not.toBeInTheDocument();
      expect(screen.getByText("Welcome")).toBeInTheDocument();
    });
  });
});
