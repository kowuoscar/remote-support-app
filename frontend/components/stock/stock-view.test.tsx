import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { StockUnitItem } from "@/lib/api/types";
import { StockView } from "./stock-view";

function smartphoneUnit(overrides: Partial<StockUnitItem> = {}): StockUnitItem {
  return {
    id: "phone-1",
    kind: "SMARTPHONE",
    agentId: "agent-1",
    agentName: "Jordan Ellis",
    agentCurrency: "USD",
    model: "Pixel 8",
    serial: "SN-001",
    status: "ACTIVE",
    fromContractId: "contract-1",
    fromClientName: "Aurora Retail Group",
    ...overrides,
  };
}

function simCardUnit(overrides: Partial<StockUnitItem> = {}): StockUnitItem {
  return {
    id: "sim-1",
    kind: "SIM_CARD",
    agentId: "agent-1",
    agentName: "Jordan Ellis",
    agentCurrency: "USD",
    number: "+1-555-0100",
    carrierName: "AT&T",
    flavor: "POSTPAID",
    postpaidPlanName: "Unlimited Premium",
    monthlyFeeAmount: 45,
    status: "ACTIVE",
    fromContractId: "contract-1",
    fromClientName: "Aurora Retail Group",
    ...overrides,
  };
}

describe("StockView", () => {
  it("shows an empty state when there is nothing in Stock", () => {
    render(<StockView units={[]} />);
    expect(screen.getByText("Nothing in Stock")).toBeInTheDocument();
  });

  it("splits units into a Smartphones table and a SIM Cards table", () => {
    render(<StockView units={[smartphoneUnit(), simCardUnit()]} />);

    expect(screen.getByText("Pixel 8")).toBeInTheDocument();
    expect(screen.getByText("SN-001")).toBeInTheDocument();
    expect(screen.getByText("+1-555-0100")).toBeInTheDocument();
    expect(screen.getByText("AT&T")).toBeInTheDocument();
    expect(screen.getByText("Unlimited Premium")).toBeInTheDocument();
    // The Contract each unit came from (ticket AC).
    expect(screen.getAllByText("Aurora Retail Group")).toHaveLength(2);
  });

  it("hides the Agent column unless showAgentColumn is set", () => {
    const { rerender } = render(<StockView units={[smartphoneUnit()]} />);
    expect(screen.queryByText("Jordan Ellis")).not.toBeInTheDocument();

    rerender(<StockView units={[smartphoneUnit()]} showAgentColumn />);
    expect(screen.getByText("Jordan Ellis")).toBeInTheDocument();
  });
});
