import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { CatalogCarrierItem, SimCardListItem, SmartphoneListItem } from "@/lib/api/types";
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
    expect(screen.getByRole("button", { name: "Edit number, +44 7700 900100" })).toBeInTheDocument();

    rerender(<SimCardFleetTable sims={[sim]} contractId="contract-1" />);
    expect(screen.getByText("+44 7700 900100")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit number, +44 7700 900100" })).not.toBeInTheDocument();
  });

  it("leaves Installed in plain text for a canCorrect table without smartphones", () => {
    render(<SimCardFleetTable sims={[sim]} contractId="contract-1" canCorrect />);

    // Only the number control offers Edit; Installed in stays read-only text.
    expect(screen.getAllByRole("button", { name: /^Edit/ })).toHaveLength(1);
    expect(screen.getByText("Pixel 8")).toBeInTheDocument();
  });

  it("renders the number control, Installed in and the status control in one row (the Agent)", () => {
    render(
      <SimCardFleetTable
        sims={[sim]}
        contractId="contract-1"
        canCorrect
        smartphones={[{ id: "phone-1", model: "Pixel 8" } as SmartphoneListItem]}
        changeStatus={() => <button type="button">Change status</button>}
      />,
    );

    const row = screen.getByRole("row", { name: /\+44 7700 900100/ });
    expect(within(row).getByRole("button", { name: "Edit number, +44 7700 900100" })).toBeInTheDocument();
    expect(within(row).getByRole("button", { name: "Change status" })).toBeInTheDocument();
    expect(within(row).getByRole("combobox")).toBeInTheDocument();
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

      expect(screen.getByRole("button", { name: "Edit plan for +44 7700 900100" })).toBeInTheDocument();
    });

    it("points to the Carriers page for a Postpaid SIM whose Carrier has no active plan (the Agent)", () => {
      render(
        <SimCardFleetTable
          sims={[postpaid]}
          contractId="contract-1"
          currency="USD"
          canCorrect
          carriers={[{ ...verizon, postpaidPlans: [] }]}
          carriersHref="/agent/carriers"
        />,
      );

      fireEvent.click(screen.getByRole("button", { name: "Edit plan for +44 7700 900100" }));
      expect(screen.getByRole("link", { name: /Carriers page/ })).toHaveAttribute("href", "/agent/carriers");
    });

    it("offers no plan control on a Prepaid SIM", () => {
      renderTable([sim], [verizon]);

      expect(screen.queryByRole("button", { name: "Edit plan for +44 7700 900100" })).not.toBeInTheDocument();
    });

    it("offers no plan control without carriers, for an archived or absent Carrier, or without canCorrect", () => {
      renderTable([postpaid]);
      expect(screen.queryByRole("button", { name: "Edit plan for +44 7700 900100" })).not.toBeInTheDocument();
      cleanup();

      renderTable([postpaid], [{ ...verizon, id: "carrier-9" }]);
      expect(screen.queryByRole("button", { name: "Edit plan for +44 7700 900100" })).not.toBeInTheDocument();
      cleanup();

      renderTable([postpaid], [{ ...verizon, archivedAt: "2024-01-01T00:00:00Z" }]);
      expect(screen.queryByRole("button", { name: "Edit plan for +44 7700 900100" })).not.toBeInTheDocument();
      cleanup();

      renderTable([{ ...postpaid, carrierId: undefined }], [verizon]);
      expect(screen.queryByRole("button", { name: "Edit plan for +44 7700 900100" })).not.toBeInTheDocument();
      cleanup();

      renderTable([postpaid], [verizon], false);
      expect(screen.queryByRole("button", { name: "Edit plan for +44 7700 900100" })).not.toBeInTheDocument();
      expect(screen.getByText("Welcome")).toBeInTheDocument();
    });
  });
});
