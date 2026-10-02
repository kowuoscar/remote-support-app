import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ClientDashboardStats } from "./dashboard-stats";

type Props = Parameters<typeof ClientDashboardStats>[0];

const BASE_PROPS: Props = { activeFleetCount: 7, openRequestsCount: 3 };

function renderStats(props: Partial<Props> = {}) {
  render(<ClientDashboardStats {...BASE_PROPS} {...props} />);
}

describe("ClientDashboardStats", () => {
  it("shows-active-fleet-count-and-meta", () => {
    renderStats();

    const card = screen.getByTestId("active-fleet-stat");
    expect(card).toHaveTextContent("Active Fleet");
    expect(card).toHaveTextContent("7");
    expect(card).toHaveTextContent("Smartphones + SIM Cards, all Contracts");
  });

  it("shows-open-requests-count-and-its-meta-naming-pending-approval-submitted-and-in-progress", () => {
    renderStats();

    const card = screen.getByTestId("open-requests-stat");
    expect(card).toHaveTextContent("3");
    expect(card).toHaveTextContent("Pending Approval, Submitted or In Progress");
  });

  it("null-active-fleet-renders-dash-sr-only-unavailable-and-couldnt-load-meta", () => {
    renderStats({ activeFleetCount: null });

    const card = screen.getByTestId("active-fleet-stat");
    const dash = screen.getByText("—");
    expect(dash).toHaveAttribute("aria-hidden", "true");
    expect(dash).toHaveClass("text-ink-mute");
    expect(card).toHaveTextContent("Unavailable");
    expect(card).toHaveTextContent("Couldn't load your Fleet");
    expect(card).not.toHaveTextContent("Smartphones + SIM Cards");
    expect(screen.getByTestId("open-requests-stat")).toHaveTextContent("3");
  });

  it("null-open-requests-renders-dash-and-couldnt-load-meta", () => {
    renderStats({ openRequestsCount: null });

    const card = screen.getByTestId("open-requests-stat");
    expect(card).toHaveTextContent("—");
    expect(card).toHaveTextContent("Unavailable");
    expect(card).toHaveTextContent("Couldn't load your Requests");
    expect(screen.getByTestId("active-fleet-stat")).toHaveTextContent("7");
  });

  it("renders-a-zero-count-as-0-not-unavailable", () => {
    renderStats({ activeFleetCount: 0, openRequestsCount: 0 });

    expect(screen.getByTestId("active-fleet-stat")).not.toHaveTextContent("—");
    expect(screen.getByTestId("open-requests-stat")).not.toHaveTextContent("—");
  });

  it("renders-the-grid-at-once-marked-dashboard-ready-with-no-skeleton", () => {
    renderStats();

    expect(screen.getByTestId("dashboard-ready")).toBeInTheDocument();
    expect(screen.queryByTestId("dashboard-loading")).not.toBeInTheDocument();
  });
});
