import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { CurrencyAmount } from "@/lib/api/types";
import { ManagerDashboardStats } from "./dashboard-stats";

const BASE_PROPS: {
  pendingApprovalsCount: number | null;
  pendingRequestsCount: number | null;
  billedThisMonth: CurrencyAmount[] | null;
  payoutThisMonth: CurrencyAmount[] | null;
  billingMonth: string | null;
  clientCount: number | null;
  agentCount: number | null;
  contractCount: number | null;
} = {
  pendingApprovalsCount: 3,
  pendingRequestsCount: 2,
  billedThisMonth: [{ currency: "USD", amount: 1000 }],
  payoutThisMonth: [{ currency: "USD", amount: 500 }],
  billingMonth: "2026-10-01",
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
    ["clients-stat", { clientCount: null }, "Couldn’t load — open Clients"],
    ["agents-stat", { agentCount: null }, "Couldn’t load — open Agents"],
    ["contracts-stat", { contractCount: null }, "Couldn’t load — open Contracts"],
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

describe("ManagerDashboardStats's money cards (manager-dashboard-money ticket)", () => {
  it("shows one currency as one Money line, with the month in the meta", () => {
    renderReady();

    const billed = screen.getByTestId("billed-stat");
    expect(billed).toHaveTextContent("$1,000.00");
    expect(billed).toHaveTextContent("Sent or approved · October 2026");
    const payout = screen.getByTestId("payout-stat");
    expect(payout).toHaveTextContent("$500.00");
    expect(payout).toHaveTextContent("Approved or paid · October 2026");
  });

  it("shows two currencies as two lines in the given order, never one sum", () => {
    renderReady({
      billedThisMonth: [
        { currency: "GBP", amount: 20 },
        { currency: "USD", amount: 135.99 },
      ],
    });

    const text = screen.getByTestId("billed-stat").textContent ?? "";
    expect(text).toContain("£20.00");
    expect(text).toContain("$135.99");
    expect(text.indexOf("£20.00")).toBeLessThan(text.indexOf("$135.99"));
    expect(text).not.toContain("155.99");
  });

  it("renders an empty list as a plain 0 with no currency symbol or code, keeping the month meta", () => {
    renderReady({ payoutThisMonth: [] });

    const payout = screen.getByTestId("payout-stat");
    expect(payout).toHaveTextContent(/^Payout this month0Approved or paid · October 2026$/);
    expect(payout).not.toHaveTextContent("$");
    expect(payout).not.toHaveTextContent("Unavailable");
  });

  it("renders null as — with its Couldn't load meta on both cards, the other cards still showing", () => {
    renderReady({ billedThisMonth: null, payoutThisMonth: null });

    for (const id of ["billed-stat", "payout-stat"]) {
      const card = screen.getByTestId(id);
      expect(card).toHaveTextContent("—");
      expect(card).toHaveTextContent("Unavailable");
      expect(card).toHaveTextContent("Couldn’t load this month’s totals");
    }
    expect(screen.getByTestId("clients-stat")).toHaveTextContent("4");
  });

  it("takes the month from billingMonth, not the clock", () => {
    renderReady({ billingMonth: "2024-02-01" });

    expect(screen.getByTestId("billed-stat")).toHaveTextContent("Sent or approved · February 2024");
  });
});
