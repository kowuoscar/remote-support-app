import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ReviewQueueItem } from "@/lib/api/types";
import { PendingApprovalsCard } from "./pending-approvals-card";

const NOW = "2026-09-17T12:00:00Z";

function clientInvoice(id: string, clientName: string, waitingSince = "2026-09-14T09:00:00Z"): ReviewQueueItem {
  return {
    kind: "CLIENT_INVOICE",
    id,
    status: "SENT",
    billingMonth: "2026-08-01",
    contractId: `contract-${id}`,
    clientName,
    agentName: "Jordan Ellis",
    currency: "USD",
    totalAmount: 70,
    waitingSince,
  };
}

function waitingList() {
  return screen.getByRole("list", { name: "Pending approvals" });
}

describe("PendingApprovalsCard", () => {
  it("lists at most the four longest-waiting invoices, in queue order, each linking to its detail page", () => {
    const items = ["Aurora", "Meridian", "Bright Path", "Harbor", "Northwind"].map((name, i) =>
      clientInvoice(`invoice-${i + 1}`, name),
    );

    render(<PendingApprovalsCard items={items} now={NOW} />);

    const links = within(waitingList()).getAllByRole("link");
    expect(links.map((link) => link.getAttribute("href"))).toEqual([
      "/manager/invoices/client/invoice-1",
      "/manager/invoices/client/invoice-2",
      "/manager/invoices/client/invoice-3",
      "/manager/invoices/client/invoice-4",
    ]);
    expect(screen.queryByText(/Northwind/)).not.toBeInTheDocument();
  });

  it("shows each invoice's type, subject, billing month, how long it has waited and its total", () => {
    render(<PendingApprovalsCard items={[clientInvoice("invoice-1", "Aurora Retail Group")]} now={NOW} />);

    const link = within(waitingList()).getByRole("link");
    expect(link).toHaveTextContent("Aurora Retail Group — Jordan Ellis");
    expect(link).toHaveTextContent("Client Invoice · August 2026 · waiting 3 days");
    expect(link).toHaveTextContent("$70.00");
  });

  it("links an Agent Invoice to its own detail page, with the Agent as its subject", () => {
    const agentInvoice = {
      kind: "AGENT_INVOICE",
      id: "agent-invoice-1",
      status: "APPROVED",
      billingMonth: "2026-08-01",
      agentId: "agent-1",
      agentName: "Camille Duforet",
      currency: "EUR",
      totalAmount: 5520.5,
      waitingSince: "2026-09-16T09:00:00Z",
    } as unknown as ReviewQueueItem;

    render(<PendingApprovalsCard items={[agentInvoice]} now={NOW} />);

    const link = within(waitingList()).getByRole("link");
    expect(link).toHaveAttribute("href", "/manager/invoices/agent/agent-invoice-1");
    expect(link).toHaveTextContent("Camille Duforet");
    expect(link).toHaveTextContent("Agent Invoice · August 2026 · waiting 1 day");
  });

  it("says so when nothing is waiting", () => {
    render(<PendingApprovalsCard items={[]} now={NOW} />);

    expect(screen.getByText("Nothing is waiting on you")).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "Pending approvals" })).not.toBeInTheDocument();
  });

  it("shows an unavailable state when the Review Queue couldn't be loaded", () => {
    render(<PendingApprovalsCard items={null} now={NOW} />);

    expect(screen.getByText("Couldn’t load the Review Queue")).toBeInTheDocument();
    expect(screen.queryByText("Nothing is waiting on you")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Review all/ })).toHaveAttribute("href", "/manager/invoices");
  });
});
