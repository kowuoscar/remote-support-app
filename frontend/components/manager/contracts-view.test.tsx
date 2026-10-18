import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import type { AgentListItem, ClientInvoiceDetail, ClientListItem, ContractListItem } from "@/lib/api/types";
import { resolveSelectedContract } from "@/lib/manager-contracts";
import { mockRouter } from "@/tests/component/next-navigation";
import type { FleetTabId } from "@/components/fleet/fleet-tabs-card";
import { ManagerContractsView, type SelectedContract } from "./contracts-view";

function contract(id: string, clientId: string, clientName: string, agentName: string): ContractListItem {
  return { id, clientId, clientName, agentId: `agent-${agentName}`, agentName, country: "FRANCE", currency: "USD" };
}

const CONTRACTS = [
  contract("c-birch", "cl-birch", "Birch Telecom", "Jordan Ellis"),
  contract("c-aurora-2", "cl-aurora", "Aurora Retail Group", "Zed Moyo"),
  contract("c-aurora-1", "cl-aurora", "Aurora Retail Group", "Amara Okafor"),
];
const CLIENTS: ClientListItem[] = [];
const AGENTS: AgentListItem[] = [];

const INVOICE: ClientInvoiceDetail = {
  id: "invoice-1",
  contractId: "c-aurora-1",
  billingMonth: "2026-10-01",
  status: "DRAFT",
  currency: "USD",
  baseAmount: 0,
  basePostpaidSims: [],
  feeLines: [],
  totalAmount: 0,
  files: [],
  filesAvailable: true,
  sentAt: null,
  approvedAt: null,
  monthSentAt: null,
  sendableOnItsOwn: false,
};

function selected(c: ContractListItem, overrides: Partial<SelectedContract> = {}): SelectedContract {
  return { contract: c, smartphones: [], simCards: [], invoice: INVOICE, carriers: [], ...overrides };
}

function renderView(contracts = CONTRACTS, id = "c-aurora-1", initialTab: FleetTabId = "sim-cards") {
  const current = contracts.find((c) => c.id === id) ?? null;
  return render(
    <ManagerContractsView
      contracts={contracts}
      clients={CLIENTS}
      agents={AGENTS}
      selected={current ? selected(current) : null}
      initialTab={initialTab}
    />,
  );
}

beforeEach(() => {
  mockRouter.replace.mockReset();
  window.history.replaceState(null, "", "/manager/contracts");
});

describe("resolveSelectedContract", () => {
  it("opens-on-the-first-client-and-its-first-agent-when-the-address-names-nothing", () => {
    expect(resolveSelectedContract(CONTRACTS, undefined)?.id).toBe("c-aurora-1");
    expect(resolveSelectedContract(CONTRACTS, "no-such-contract")?.id).toBe("c-aurora-1");
    expect(resolveSelectedContract(CONTRACTS, "c-birch")?.id).toBe("c-birch");
    expect(resolveSelectedContract([], undefined)).toBeNull();
  });
});

describe("ManagerContractsView", () => {
  it("lists-clients-holding-contracts-alphabetically-with-the-selected-current", () => {
    renderView();
    const nav = screen.getByRole("navigation", { name: "Clients" });
    const links = within(nav).getAllByRole("link");
    expect(links.map((l) => l.textContent)).toEqual(["Aurora Retail Group", "Birch Telecom"]);
    expect(links[0]).toHaveAttribute("aria-current", "true");
    expect(links[0]).toHaveAttribute("href", "?contract=c-aurora-1");
    expect(links[1]).toHaveAttribute("href", "?contract=c-birch");
  });

  it("shows-the-client-heading-agent-select-country-and-currency", () => {
    renderView();
    expect(screen.getByRole("heading", { level: 2, name: "Aurora Retail Group" })).toBeInTheDocument();
    const select = screen.getByRole("combobox", { name: "Agent" });
    expect(within(select).getAllByRole("option").map((o) => o.textContent)).toEqual(["Amara Okafor", "Zed Moyo"]);
    expect(select).toHaveValue("c-aurora-1");
    expect(screen.getByText("France · USD")).toBeInTheDocument();
  });

  it("shows-the-agent-select-even-for-one-agent", () => {
    renderView(CONTRACTS, "c-birch");
    const select = screen.getByRole("combobox", { name: "Agent" });
    expect(within(select).getAllByRole("option")).toHaveLength(1);
  });

  it("picking-an-agent-replaces-the-address-keeping-the-tab", async () => {
    renderView(CONTRACTS, "c-aurora-1", "smartphones");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Agent" }), "c-aurora-2");
    expect(mockRouter.replace).toHaveBeenCalledWith("/manager/contracts?contract=c-aurora-2&tab=smartphones", {
      scroll: false,
    });
  });

  it("picking-a-client-replaces-the-address", async () => {
    renderView();
    const nav = screen.getByRole("navigation", { name: "Clients" });
    await userEvent.click(within(nav).getByRole("link", { name: "Birch Telecom" }));
    expect(mockRouter.replace).toHaveBeenCalledWith("/manager/contracts?contract=c-birch", { scroll: false });
  });

  it("shows-new-contract-the-invoice-summary-and-the-fleet-tabs", () => {
    renderView();
    expect(screen.getAllByRole("button", { name: "New Contract" }).length).toBeGreaterThan(0);
    expect(screen.getByRole("link", { name: /Open invoice/ })).toHaveAttribute(
      "href",
      "/manager/invoices/client/invoice-1",
    );
    expect(screen.getByRole("tablist", { name: "Fleet" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add SIM card" })).toBeInTheDocument();
    expect(screen.queryByRole("searchbox")).not.toBeInTheDocument();
    expect(screen.queryByText(/\d+ of \d+/)).not.toBeInTheDocument();
  });

  it("shows-add-smartphone-on-the-smartphones-tab", () => {
    renderView(CONTRACTS, "c-aurora-1", "smartphones");
    expect(screen.getByRole("button", { name: "Add smartphone" })).toBeInTheDocument();
  });

  it("omits-the-summary-when-the-invoice-read-failed", () => {
    render(
      <ManagerContractsView
        contracts={CONTRACTS}
        clients={CLIENTS}
        agents={AGENTS}
        selected={selected(CONTRACTS[1], { invoice: null })}
        initialTab="sim-cards"
      />,
    );
    expect(screen.queryByRole("link", { name: /Open invoice/ })).not.toBeInTheDocument();
  });

  it("marks-the-detail-area-busy-while-a-pick-is-pending", async () => {
    // A replace that never settles keeps the transition pending, as a slow server render does.
    mockRouter.replace.mockImplementation(() => new Promise(() => {}));
    renderView();
    const heading = screen.getByRole("heading", { level: 2, name: "Aurora Retail Group" });
    expect(heading.closest("[aria-busy]")).toHaveAttribute("aria-busy", "false");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Agent" }), "c-aurora-2");
    expect(screen.getByRole("heading", { level: 2, name: "Aurora Retail Group" }).closest("[aria-busy]")).toHaveAttribute(
      "aria-busy",
      "true",
    );
  });

  it("with-no-contracts-shows-the-empty-state-under-new-contract-and-no-landmark", () => {
    renderView([], "");
    expect(screen.getByText("No contracts yet")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "New Contract" }).length).toBeGreaterThan(0);
    expect(screen.queryByRole("navigation", { name: "Clients" })).not.toBeInTheDocument();
  });
});
