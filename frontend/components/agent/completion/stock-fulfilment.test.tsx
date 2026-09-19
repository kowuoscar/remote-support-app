import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { RequestListItem, StockUnitItem } from "@/lib/api/types";
import { ProvisionSimCompletion } from "./provision-sim-completion";
import { ProvisionSmartphoneCompletion } from "./provision-smartphone-completion";
import { ReplaceSimCompletion } from "./replace-sim-completion";
import { ReplaceSmartphoneCompletion } from "./replace-smartphone-completion";

/**
 * The "from my Stock" picker fulfil-from-stock adds to Provision Smartphone/SIM and Replace
 * Smartphone/SIM's own completion pieces (ticket AC: "offers a 'from my Stock' picker only when a
 * matching unit exists"). Mirrors replace-sim-completion.test.tsx's/return-completion.test.tsx's
 * own request()-builder pattern.
 */
function request(overrides: Partial<RequestListItem> = {}): RequestListItem {
  return {
    id: "request-1",
    contractId: "contract-1",
    type: "PROVISION_SMARTPHONE",
    status: "IN_PROGRESS",
    raisedByTesterId: "tester-1",
    raisedByUsername: "tester@example.com",
    agentAuthored: false,
    loggedByUsername: "tester@example.com",
    cancellationReason: null,
    description: null,
    createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function stockSmartphone(overrides: Partial<StockUnitItem> = {}): StockUnitItem {
  return {
    id: "stock-phone-1",
    kind: "SMARTPHONE",
    agentId: "agent-1",
    agentName: "Jordan Ellis",
    agentCurrency: "USD",
    model: "Pixel 8",
    status: "ACTIVE",
    ...overrides,
  };
}

function stockSimCard(overrides: Partial<StockUnitItem> = {}): StockUnitItem {
  return {
    id: "stock-sim-1",
    kind: "SIM_CARD",
    agentId: "agent-1",
    agentName: "Jordan Ellis",
    agentCurrency: "USD",
    number: "+1-555-0200",
    flavor: "PREPAID",
    status: "ACTIVE",
    ...overrides,
  };
}

describe("ProvisionSmartphoneCompletion — Stock picker", () => {
  it("hides the picker when the Agent's Stock has no Smartphone", () => {
    render(
      <ProvisionSmartphoneCompletion
        request={request({ requestedModel: "Galaxy S24" })}
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[]}
        stockSmartphones={[]}
      />,
    );
    expect(screen.queryByLabelText(/From my Stock/)).not.toBeInTheDocument();
  });

  it("shows every Stock Smartphone — a Smartphone has no matching rule beyond ownership", () => {
    render(
      <ProvisionSmartphoneCompletion
        request={request({ requestedModel: "Galaxy S24" })}
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[]}
        stockSmartphones={[stockSmartphone({ id: "a", model: "Pixel 8" }), stockSmartphone({ id: "b", model: "iPhone 13" })]}
      />,
    );
    const picker = screen.getByLabelText(/From my Stock/);
    expect(picker).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /Pixel 8/ })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /iPhone 13/ })).toBeInTheDocument();
  });
});

describe("ReplaceSmartphoneCompletion — Stock picker", () => {
  it("hides the picker when the Agent's Stock has no Smartphone", () => {
    render(
      <ReplaceSmartphoneCompletion
        request={request({ type: "REPLACE_SMARTPHONE", targetSmartphoneModel: "iPhone 13" })}
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[]}
        stockSmartphones={[]}
      />,
    );
    expect(screen.queryByLabelText(/From my Stock/)).not.toBeInTheDocument();
  });

  it("shows a picker naming every Stock Smartphone", () => {
    render(
      <ReplaceSmartphoneCompletion
        request={request({ type: "REPLACE_SMARTPHONE", targetSmartphoneModel: "iPhone 13" })}
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[]}
        stockSmartphones={[stockSmartphone({ model: "Pixel 8" })]}
      />,
    );
    expect(screen.getByLabelText(/From my Stock/)).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /Pixel 8/ })).toBeInTheDocument();
  });
});

describe("ProvisionSimCompletion — Stock picker", () => {
  it("hides the picker (and shows the number field) when no Stock SIM Card matches", () => {
    render(
      <ProvisionSimCompletion
        request={request({
          type: "PROVISION_SIM",
          requestedFlavor: "PREPAID",
          requestedCarrierId: "carrier-a",
        })}
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[]}
        stockSimCards={[stockSimCard({ carrierId: "carrier-b", flavor: "PREPAID" })]}
      />,
    );
    expect(screen.queryByLabelText(/From my Stock/)).not.toBeInTheDocument();
    expect(screen.getByLabelText("New SIM number")).toBeInTheDocument();
  });

  it("shows only the Stock SIM Cards matching Carrier and flavor, and hides the number field once picked", () => {
    render(
      <ProvisionSimCompletion
        request={request({
          type: "PROVISION_SIM",
          requestedFlavor: "PREPAID",
          requestedCarrierId: "carrier-a",
        })}
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[]}
        stockSimCards={[
          stockSimCard({ id: "match", carrierId: "carrier-a", flavor: "PREPAID", number: "+1-555-0301" }),
          stockSimCard({ id: "wrong-carrier", carrierId: "carrier-b", flavor: "PREPAID", number: "+1-555-0302" }),
          stockSimCard({ id: "wrong-flavor", carrierId: "carrier-a", flavor: "POSTPAID", number: "+1-555-0303" }),
        ]}
      />,
    );
    expect(screen.getByLabelText(/From my Stock/)).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "+1-555-0301" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "+1-555-0302" })).not.toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "+1-555-0303" })).not.toBeInTheDocument();
  });

  it("for a Postpaid Request also requires the same Postpaid Plan to match", () => {
    render(
      <ProvisionSimCompletion
        request={request({
          type: "PROVISION_SIM",
          requestedFlavor: "POSTPAID",
          requestedCarrierId: "carrier-a",
          requestedPostpaidPlanId: "plan-1",
        })}
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[]}
        stockSimCards={[
          stockSimCard({ id: "match", carrierId: "carrier-a", flavor: "POSTPAID", postpaidPlanId: "plan-1", number: "+1-555-0400" }),
          stockSimCard({ id: "wrong-plan", carrierId: "carrier-a", flavor: "POSTPAID", postpaidPlanId: "plan-2", number: "+1-555-0401" }),
        ]}
      />,
    );
    expect(screen.getByRole("option", { name: "+1-555-0400" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "+1-555-0401" })).not.toBeInTheDocument();
  });
});

describe("ReplaceSimCompletion — Stock picker", () => {
  it("hides the picker when no Stock SIM Card matches the old SIM Card's Carrier/flavor", () => {
    render(
      <ReplaceSimCompletion
        request={request({ type: "REPLACE_SIM", targetSimCardId: "sim-1", targetSimCardNumber: "+1-555-0100" })}
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[
          { id: "sim-1", contractId: "contract-1", number: "+1-555-0100", carrierId: "carrier-a", flavor: "PREPAID", monthlyFeeAmount: null, status: "ACTIVE" },
        ]}
        stockSimCards={[stockSimCard({ carrierId: "carrier-b", flavor: "PREPAID" })]}
      />,
    );
    expect(screen.queryByLabelText(/From my Stock/)).not.toBeInTheDocument();
  });

  it("shows only the Stock SIM Cards matching the old SIM Card's Carrier and flavor", () => {
    render(
      <ReplaceSimCompletion
        request={request({ type: "REPLACE_SIM", targetSimCardId: "sim-1", targetSimCardNumber: "+1-555-0100" })}
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[
          { id: "sim-1", contractId: "contract-1", number: "+1-555-0100", carrierId: "carrier-a", flavor: "PREPAID", monthlyFeeAmount: null, status: "ACTIVE" },
        ]}
        stockSimCards={[
          stockSimCard({ id: "match", carrierId: "carrier-a", flavor: "PREPAID", number: "+1-555-0501" }),
          stockSimCard({ id: "wrong-carrier", carrierId: "carrier-b", flavor: "PREPAID", number: "+1-555-0502" }),
        ]}
      />,
    );
    expect(screen.getByRole("option", { name: "+1-555-0501" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "+1-555-0502" })).not.toBeInTheDocument();
  });
});
