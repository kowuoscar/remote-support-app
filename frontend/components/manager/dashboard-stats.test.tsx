import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ManagerDashboardStats } from "./dashboard-stats";

const BASE_PROPS: {
  pendingApprovalsCount: number | null;
  pendingRequestsCount: number | null;
  billedThisMonth: number;
  payoutThisMonth: number;
  clientCount: number;
  agentCount: number;
  contractCount: number;
} = {
  pendingApprovalsCount: 3,
  pendingRequestsCount: 2,
  billedThisMonth: 1000,
  payoutThisMonth: 500,
  clientCount: 4,
  agentCount: 5,
  contractCount: 6,
};

/** `useSimulatedLoad`'s skeleton delay — advanced past in every test so the real stats render. */
async function renderReady(props: Partial<typeof BASE_PROPS> = {}) {
  render(<ManagerDashboardStats {...BASE_PROPS} {...props} />);
  await act(async () => {
    vi.advanceTimersByTime(600);
  });
}

describe("ManagerDashboardStats's Pending Requests stat (manager-approves-requests ticket)", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows the Pending Requests count, linking to the Pending Requests page", async () => {
    await renderReady({ pendingRequestsCount: 7 });

    expect(screen.getByTestId("pending-requests-stat")).toHaveTextContent("7");
    const link = screen.getByRole("link", { name: /Review requests/ });
    expect(link).toHaveAttribute("href", "/manager/requests");
  });

  it("shows an unavailable state, still linking to the page, when it couldn't be loaded", async () => {
    await renderReady({ pendingRequestsCount: null });

    expect(screen.getByTestId("pending-requests-stat")).toHaveTextContent("—");
    const link = screen.getByRole("link", { name: /Couldn.t load/ });
    expect(link).toHaveAttribute("href", "/manager/requests");
  });

  it("keeps the Review Queue's own Pending approvals stat separate", async () => {
    await renderReady({ pendingApprovalsCount: 3, pendingRequestsCount: 2 });

    expect(screen.getByTestId("pending-approvals-stat")).toHaveTextContent("3");
    expect(screen.getByTestId("pending-requests-stat")).toHaveTextContent("2");
  });
});
