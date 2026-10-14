import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ManagerDashboardStats } from "./dashboard-stats";

const BASE_PROPS: {
  pendingApprovalsCount: number | null;
  pendingRequestsCount: number | null;
  billedThisMonth: number;
  payoutThisMonth: number;
  clientCount: number | null;
  agentCount: number | null;
  contractCount: number | null;
} = {
  pendingApprovalsCount: 3,
  pendingRequestsCount: 2,
  billedThisMonth: 1000,
  payoutThisMonth: 500,
  clientCount: 4,
  agentCount: 5,
  contractCount: 6,
};

function renderReady(props: Partial<typeof BASE_PROPS> = {}) {
  render(<ManagerDashboardStats {...BASE_PROPS} {...props} />);
}

describe("ManagerDashboardStats's grid (manager-dashboard-identity-and-counts ticket)", () => {
  it("renders at once as dashboard-ready, with no skeleton first", () => {
    renderReady();

    expect(screen.getByTestId("dashboard-ready")).toBeInTheDocument();
    expect(screen.queryByTestId("dashboard-loading")).not.toBeInTheDocument();
  });

  it("shows the Client, Agent and Contract counts as numbers", () => {
    renderReady({ clientCount: 2, agentCount: 3, contractCount: 3 });

    expect(screen.getByTestId("clients-stat")).toHaveTextContent("2");
    expect(screen.getByTestId("agents-stat")).toHaveTextContent("3");
    expect(screen.getByTestId("contracts-stat")).toHaveTextContent("3");
  });

  it("captions the Clients card 'In this tenant', not 'Active'", () => {
    renderReady();

    expect(screen.getByTestId("clients-stat")).toHaveTextContent("In this tenant");
    expect(screen.getByTestId("clients-stat")).not.toHaveTextContent("Active");
  });

  it.each([
    ["clients-stat", { clientCount: null }, "Couldn't load your Clients"],
    ["agents-stat", { agentCount: null }, "Couldn't load your Agents"],
    ["contracts-stat", { contractCount: null }, "Couldn't load your Contracts"],
  ])("%s reads — with its own Couldn't load line when unavailable, the rest still showing", (testId, props, meta) => {
    renderReady(props);

    const card = screen.getByTestId(testId);
    expect(card).toHaveTextContent("—");
    expect(card).toHaveTextContent("Unavailable");
    expect(card).toHaveTextContent(meta);
    expect(card).not.toHaveTextContent("0");
    expect(screen.getByTestId("pending-approvals-stat")).toHaveTextContent("3");
  });
});

describe("ManagerDashboardStats's Pending Requests stat (manager-approves-requests ticket)", () => {
  it("shows the Pending Requests count, linking to the Pending Requests page", () => {
    renderReady({ pendingRequestsCount: 7 });

    expect(screen.getByTestId("pending-requests-stat")).toHaveTextContent("7");
    const link = screen.getByRole("link", { name: /Review requests/ });
    expect(link).toHaveAttribute("href", "/manager/requests");
  });

  it("shows an unavailable state, still linking to the page, when it couldn't be loaded", () => {
    renderReady({ pendingRequestsCount: null });

    expect(screen.getByTestId("pending-requests-stat")).toHaveTextContent("—");
    const link = screen.getByRole("link", { name: /Couldn.t load/ });
    expect(link).toHaveAttribute("href", "/manager/requests");
  });

  it("keeps the Review Queue's own Pending approvals stat separate", () => {
    renderReady({ pendingApprovalsCount: 3, pendingRequestsCount: 2 });

    expect(screen.getByTestId("pending-approvals-stat")).toHaveTextContent("3");
    expect(screen.getByTestId("pending-requests-stat")).toHaveTextContent("2");
  });
});
