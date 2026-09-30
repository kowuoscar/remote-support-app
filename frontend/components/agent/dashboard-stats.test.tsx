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

  it("reads the Local Support Fees meta as a running total while the invoice is Draft", () => {
    renderStats({ latestInvoiceStatus: "DRAFT" });

    expect(screen.getByText("Running total, all your Contracts")).toBeInTheDocument();
    expect(screen.queryByText("As sent on your invoice")).not.toBeInTheDocument();
  });

  it.each<AgentInvoiceStatusValue>(["SENT", "APPROVED", "PAID"])(
    "reads the Local Support Fees meta as frozen once the invoice is %s",
    (status) => {
      renderStats({ latestInvoiceStatus: status });

      expect(screen.getByText("As sent on your invoice")).toBeInTheDocument();
      expect(screen.queryByText("Running total, all your Contracts")).not.toBeInTheDocument();
    },
  );

  it("shows the invoice's own month on the Local Support Fees and My Invoice status cards", () => {
    renderStats({ currentMonthLabel: "August 2026", latestInvoiceMonth: "August 2026" });

    expect(screen.getByText("Local Support Fees — August 2026")).toBeInTheDocument();
    expect(screen.getByText("August 2026")).toBeInTheDocument();
  });

  it("renders both invoice cards unavailable while the other cards still render", () => {
    renderStats({
      currentMonthLabel: null,
      runningLocalSupportFees: null,
      latestInvoiceMonth: null,
      latestInvoiceStatus: null,
    });

    expect(screen.getByText("Local Support Fees")).toBeInTheDocument();
    expect(screen.getAllByText(/Couldn.t load your invoice/)).toHaveLength(2);
    expect(screen.getByText("4")).toBeInTheDocument();
    expect(screen.getByText("$3,200.00")).toBeInTheDocument();
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

  it("renders Open Requests as — with Couldn't load your Requests while the other cards still render", () => {
    renderStats({ openRequestsCount: null });

    const card = screen.getByTestId("open-requests-stat");
    expect(card).toHaveTextContent("—");
    expect(card).toHaveTextContent("Couldn't load your Requests");
    expect(card).not.toHaveTextContent("Submitted or In Progress");
    expect(screen.getByText("$1,250.00")).toBeInTheDocument();
    expect(screen.getByText("$3,200.00")).toBeInTheDocument();
  });

  it("renders a zero Open Requests count as 0, not as unavailable", () => {
    renderStats({ openRequestsCount: 0 });

    const card = screen.getByTestId("open-requests-stat");
    expect(card).toHaveTextContent("0");
    expect(card).not.toHaveTextContent("—");
    expect(card).toHaveTextContent("Submitted or In Progress");
  });
});
