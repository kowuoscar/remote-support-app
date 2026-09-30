import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AgentDashboardStats } from "./dashboard-stats";
import type { AgentInvoiceStatusValue } from "@/lib/api/types";

type Props = Parameters<typeof AgentDashboardStats>[0];

const BASE_PROPS: Props = {
  currentMonthLabel: "September 2026",
  runningLocalSupportFees: 1250,
  currency: "USD",
  openRequestsCount: 4,
  latestInvoiceMonth: "September 2026",
  latestInvoiceStatus: "DRAFT",
  salary: 3200,
  rolloutAdvance: 500,
};

function renderStats(props: Partial<Props> = {}) {
  render(<AgentDashboardStats {...BASE_PROPS} {...props} />);
}

describe("AgentDashboardStats", () => {
  it("renders the grid at once, marked dashboard-ready, with no skeleton", () => {
    renderStats();

    expect(screen.getByTestId("dashboard-ready")).toBeInTheDocument();
    expect(screen.queryByTestId("dashboard-loading")).not.toBeInTheDocument();
    expect(screen.getByText("Standing salary + advance")).toBeInTheDocument();
  });

  it("shows the standing salary and the Rollout Advance in the Agent's own currency", () => {
    renderStats({ currency: "USD", salary: 3200, rolloutAdvance: 500 });

    expect(screen.getByText("$3,200.00")).toBeInTheDocument();
    expect(screen.getByText("+ $500 Rollout Advance")).toBeInTheDocument();
  });

  it("shows a zero Rollout Advance when none was set", () => {
    renderStats({ rolloutAdvance: 0 });

    expect(screen.getByText("+ $0 Rollout Advance")).toBeInTheDocument();
  });

  it("shows the figures and metas from props", () => {
    renderStats();

    expect(screen.getByText("Local Support Fees — September 2026")).toBeInTheDocument();
    expect(screen.getByText("$1,250.00")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
    expect(screen.getByText("Submitted or In Progress")).toBeInTheDocument();
  });

  it("renders — with a Couldn't load meta for each unavailable region", () => {
    renderStats({
      runningLocalSupportFees: null,
      openRequestsCount: null,
      latestInvoiceStatus: null,
      latestInvoiceMonth: null,
    });

    expect(screen.getAllByText("—")).toHaveLength(3);
    expect(screen.getAllByText(/Couldn.t load your invoice/)).toHaveLength(2);
    expect(screen.getByText(/Couldn.t load your Requests/)).toBeInTheDocument();
  });

  it.each<[AgentInvoiceStatusValue, string]>([
    ["DRAFT", "Draft"],
    ["SENT", "Awaiting approval"],
    ["APPROVED", "Approved"],
    ["PAID", "Paid"],
  ])("labels a %s invoice %s", (status, label) => {
    renderStats({ latestInvoiceStatus: status });

    expect(screen.getByText(label)).toBeInTheDocument();
  });
});
