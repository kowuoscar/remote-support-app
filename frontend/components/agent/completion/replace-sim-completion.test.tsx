import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { CatalogCarrierItem, RequestListItem, SimCardListItem } from "@/lib/api/types";
import { ReplaceSimCompletion } from "./replace-sim-completion";

function request(overrides: Partial<RequestListItem> = {}): RequestListItem {
  return {
    id: "request-1",
    contractId: "contract-1",
    type: "REPLACE_SIM",
    status: "IN_PROGRESS",
    raisedByTesterId: "tester-1",
    raisedByUsername: "tester@example.com",
    agentAuthored: false,
    loggedByUsername: "tester@example.com",
    cancellationReason: null,
    description: null,
    createdAt: "2026-01-01T00:00:00Z",
    targetSimCardId: "sim-1",
    targetSimCardNumber: "+1-555-0100",
    ...overrides,
  };
}

function carrier(id: string, plans: { id: string; archivedAt: string | null }[] = []): CatalogCarrierItem {
  return {
    id,
    country: "UNITED_STATES",
    name: `Carrier ${id}`,
    archivedAt: null,
    topupOptions: [],
    postpaidPlans: plans.map((plan) => ({ ...plan, carrierId: id, name: `Plan ${plan.id}`, price: 10 })),
  };
}

function simCard(overrides: Partial<SimCardListItem> = {}): SimCardListItem {
  return {
    id: "sim-1",
    contractId: "contract-1",
    number: "+1-555-0100",
    flavor: "PREPAID",
    monthlyFeeAmount: null,
    status: "ACTIVE",
    ...overrides,
  };
}

describe("ReplaceSimCompletion", () => {
  it("defaults the Carrier and Flavor from the old SIM Card's own current values", () => {
    const carrierA = carrier("carrier-a");
    render(
      <ReplaceSimCompletion
        request={request()}
        carriers={[carrierA]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[simCard({ carrierId: "carrier-a", flavor: "PREPAID" })]}
      />,
    );

    expect(screen.getByRole("combobox", { name: "Carrier" })).toHaveValue("carrier-a");
    expect(screen.getByRole("combobox", { name: "Flavor" })).toHaveValue("PREPAID");
    expect(screen.getByLabelText("New SIM number")).toHaveValue("");
  });

  it("defaults to Postpaid with the old SIM Card's own Plan pre-selected", () => {
    const carrierA = carrier("carrier-a", [{ id: "plan-1", archivedAt: null }]);
    render(
      <ReplaceSimCompletion
        request={request()}
        carriers={[carrierA]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[simCard({ carrierId: "carrier-a", flavor: "POSTPAID", postpaidPlanId: "plan-1" })]}
      />,
    );

    expect(screen.getByRole("combobox", { name: "Flavor" })).toHaveValue("POSTPAID");
    expect(screen.getByRole("combobox", { name: "Postpaid plan" })).toHaveValue("plan-1");
  });

  it("does not default to an archived Carrier or Plan — the picker starts blank instead", () => {
    render(
      <ReplaceSimCompletion
        request={request()}
        // The old SIM Card's Carrier isn't in the active catalog at all (archived) — nothing to
        // default to.
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[simCard({ carrierId: "carrier-a", flavor: "PREPAID" })]}
      />,
    );

    expect(screen.getByText("No active carrier for this country yet.", { exact: false })).toBeInTheDocument();
  });

  it("names the SIM Card being retired", () => {
    render(
      <ReplaceSimCompletion
        request={request({ targetSimCardNumber: "+1-555-0199" })}
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[]}
      />,
    );

    expect(screen.getByText(/Retires/)).toHaveTextContent("+1-555-0199");
  });
});
