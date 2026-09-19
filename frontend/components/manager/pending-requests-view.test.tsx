import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { PendingRequestItem, RequestListItem } from "@/lib/api/types";
import { PendingRequestsView } from "./pending-requests-view";

const NOW = "2026-09-17T12:00:00Z";

function requestListItem(overrides: Partial<RequestListItem> = {}): RequestListItem {
  return {
    id: "request-1",
    contractId: "contract-1",
    type: "PROVISION_SMARTPHONE",
    status: "PENDING_APPROVAL",
    raisedByTesterId: "tester-1",
    raisedByUsername: "priya.raman@aurora.example",
    agentAuthored: false,
    loggedByUsername: "priya.raman@aurora.example",
    cancellationReason: null,
    description: null,
    createdAt: "2026-09-14T09:00:00Z",
    ...overrides,
  };
}

function pendingItem(overrides: Partial<PendingRequestItem> = {}): PendingRequestItem {
  return {
    request: requestListItem(),
    clientName: "Aurora Retail Group",
    agentName: "Jordan Ellis",
    waitingSince: "2026-09-14T09:00:00Z",
    ...overrides,
  };
}

describe("PendingRequestsView", () => {
  it("shows each pending Request's type, Client, Tester, Agent, details and how long it has waited", () => {
    render(
      <PendingRequestsView
        items={[pendingItem({ request: requestListItem({ requestedModel: "iPhone 15" }) })]}
        now={NOW}
      />,
    );

    const row = screen.getByRole("row", { name: /Aurora Retail Group/ });
    expect(within(row).getByText("Provision Smartphone")).toBeInTheDocument();
    expect(within(row).getByText("Aurora Retail Group")).toBeInTheDocument();
    expect(within(row).getByText("priya.raman@aurora.example")).toBeInTheDocument();
    expect(within(row).getByText("Jordan Ellis")).toBeInTheDocument();
    expect(within(row).getByText("Requested: iPhone 15")).toBeInTheDocument();
    expect(within(row).getByText("3 days")).toBeInTheDocument();
  });

  it("shows a Replace Request's target as the unit that would be retired", () => {
    render(
      <PendingRequestsView
        items={[
          pendingItem({
            request: requestListItem({
              type: "REPLACE_SMARTPHONE",
              targetSmartphoneId: "phone-1",
              targetSmartphoneModel: "Pixel 8",
            }),
          }),
        ]}
        now={NOW}
      />,
    );

    expect(screen.getByText("Replacing: Pixel 8")).toBeInTheDocument();
  });

  it("shows who logged an Agent-authored Request, alongside the Tester it's for", () => {
    render(
      <PendingRequestsView
        items={[
          pendingItem({
            request: requestListItem({
              agentAuthored: true,
              raisedByUsername: "priya.raman@aurora.example",
              loggedByUsername: "agent@example.com",
            }),
          }),
        ]}
        now={NOW}
      />,
    );

    expect(screen.getByText("Logged by agent@example.com")).toBeInTheDocument();
  });

  it("offers approve and reject controls on every row", () => {
    render(<PendingRequestsView items={[pendingItem()]} now={NOW} />);

    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reject" })).toBeInTheDocument();
  });

  it("shows the empty state when nothing is Pending Approval", () => {
    render(<PendingRequestsView items={[]} now={NOW} />);

    expect(screen.getByText("Nothing is waiting on you")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
});
