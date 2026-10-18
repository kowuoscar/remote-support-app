import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import type { ClientInvoiceQueueItem, ReviewAgent, ReviewMonth } from "@/lib/api/types";
import { mockRouter } from "@/tests/component/next-navigation";
import { ManagerInvoicesView } from "./manager-invoices-view";

const NOW = "2026-10-17T12:00:00Z";

const AGENTS: ReviewAgent[] = [
  { agentId: "agent-avery", agentName: "Avery Stone", waiting: 3 },
  { agentId: "agent-blake", agentName: "Blake Moss", waiting: 0 },
];

const QUEUE: ClientInvoiceQueueItem[] = [
  {
    kind: "CLIENT_INVOICE",
    id: "invoice-1",
    status: "SENT",
    billingMonth: "2026-09-01",
    contractId: "contract-1",
    clientName: "Aurora Retail Group",
    agentId: null,
    agentName: "Avery Stone",
    currency: "USD",
    totalAmount: 70,
    waitingSince: "2026-10-14T09:00:00Z",
  },
];

const REVIEW: ReviewMonth = {
  agentId: "agent-avery",
  agentName: "Avery Stone",
  billingMonth: "2026-10-01",
  monthSentAt: "2026-10-12T12:00:00Z",
  clientInvoices: [
    {
      id: "invoice-1",
      contractId: "contract-1",
      clientName: "Aurora Retail Group",
      reviewStatus: "TO_REVIEW",
      currency: "USD",
      totalAmount: 70,
      sentAt: "2026-10-12T12:00:00Z",
      approvedAt: null,
      sentBackAt: null,
    },
  ],
  agentInvoice: null,
};

function renderView(props: Partial<Parameters<typeof ManagerInvoicesView>[0]> = {}) {
  return render(
    <ManagerInvoicesView items={QUEUE} now={NOW} initialFilter="ALL" agents={AGENTS} selected={null} {...props} />,
  );
}

beforeEach(() => {
  mockRouter.replace.mockReset();
});

describe("ManagerInvoicesView side list", () => {
  it("lists All Agents first, then every Agent in the server's order, each with its count", () => {
    renderView();

    const nav = screen.getByRole("navigation", { name: "Agents" });
    const links = within(nav).getAllByRole("link");
    expect(links.map((link) => link.getAttribute("href"))).toEqual([
      "/manager/invoices",
      "/manager/invoices?agent=agent-avery",
      "/manager/invoices?agent=agent-blake",
    ]);
    expect(within(nav).getByRole("link", { name: "All Agents 3 waiting" })).toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "Avery Stone 3 waiting" })).toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "Blake Moss nothing waiting" })).toBeInTheDocument();
  });

  it("shows a dash, not a zero, beside an Agent with nothing waiting", () => {
    renderView();

    const blake = screen.getByRole("link", { name: "Blake Moss nothing waiting" });
    expect(blake).toHaveTextContent("—");
    expect(blake).not.toHaveTextContent("0");
  });

  it("selects All Agents by default and shows the Review Queue", () => {
    renderView();

    const nav = screen.getByRole("navigation", { name: "Agents" });
    expect(within(nav).getByRole("link", { name: "All Agents 3 waiting" })).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("tablist", { name: "Filter the Review Queue by invoice type" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Review Client Invoice: Aurora Retail Group — Avery Stone, September 2026/ })).toBeInTheDocument();
  });

  it("writes a pick to the address with router.replace, dropping the month and the type", async () => {
    renderView();

    await userEvent.click(screen.getByRole("link", { name: "Avery Stone 3 waiting" }));

    expect(mockRouter.replace).toHaveBeenCalledWith("/manager/invoices?agent=agent-avery", { scroll: false });
  });

  it("returns to the plain address when All Agents is picked", async () => {
    renderView({
      selected: { agentId: "agent-avery", months: [{ billingMonth: "2026-10-01", waiting: 1 }], billingMonth: "2026-10-01", review: REVIEW },
    });

    await userEvent.click(screen.getByRole("link", { name: "All Agents 3 waiting" }));

    expect(mockRouter.replace).toHaveBeenCalledWith("/manager/invoices", { scroll: false });
  });

  it("selects the picked Agent and shows their month instead of the queue", () => {
    renderView({
      selected: { agentId: "agent-avery", months: [{ billingMonth: "2026-10-01", waiting: 1 }], billingMonth: "2026-10-01", review: REVIEW },
    });

    const nav = screen.getByRole("navigation", { name: "Agents" });
    expect(within(nav).getByRole("link", { name: "Avery Stone 3 waiting" })).toHaveAttribute("aria-current", "true");
    expect(within(nav).getByRole("link", { name: "All Agents 3 waiting" })).not.toHaveAttribute("aria-current");
    expect(screen.getByRole("region", { name: "Month" })).toBeInTheDocument();
    expect(screen.queryByRole("tablist")).not.toBeInTheDocument();
  });
});

describe("ManagerInvoicesView dropdown", () => {
  it("offers the same rows with their count as the second line", async () => {
    renderView();

    await userEvent.click(screen.getByRole("button", { name: /All Agents/ }));

    const options = within(screen.getByRole("listbox")).getAllByRole("option");
    expect(options.map((option) => option.textContent)).toEqual([
      "All Agents3 waiting",
      "Avery Stone3 waiting",
      "Blake MossNothing waiting",
    ]);
  });

  it("writes a pick to the address", async () => {
    renderView();

    await userEvent.click(screen.getByRole("button", { name: /All Agents/ }));
    await userEvent.click(screen.getByRole("option", { name: /Blake Moss/ }));

    expect(mockRouter.replace).toHaveBeenCalledWith("/manager/invoices?agent=agent-blake", { scroll: false });
  });
});

describe("ManagerInvoicesView without Agents", () => {
  it.each([
    ["a tenant with no Agent", [] as ReviewAgent[]],
    ["a failed Agents read", null],
  ])("shows the queue alone for %s", (_name, agents) => {
    renderView({ agents });

    expect(screen.queryByRole("navigation", { name: "Agents" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /All Agents/ })).not.toBeInTheDocument();
    expect(screen.getByRole("tablist", { name: "Filter the Review Queue by invoice type" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Review Client Invoice: Aurora Retail Group/ })).toBeInTheDocument();
  });

  it("says the queue failed to load, with the side list still there", () => {
    renderView({ items: null });

    expect(screen.getByText("Couldn't load the Review Queue")).toBeInTheDocument();
    expect(screen.getByRole("navigation", { name: "Agents" })).toBeInTheDocument();
  });
});
