import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { AgentInvoiceQueueItem, ClientInvoiceQueueItem } from "@/lib/api/types";
import { ReviewQueueView } from "./review-queue-view";

const NOW = "2026-09-17T12:00:00Z";

function clientInvoice(overrides: Partial<ClientInvoiceQueueItem> = {}): ClientInvoiceQueueItem {
  return {
    kind: "CLIENT_INVOICE",
    id: "invoice-1",
    status: "SENT",
    billingMonth: "2026-08-01",
    contractId: "contract-1",
    clientName: "Aurora Retail Group",
    agentId: null,
    agentName: "Jordan Ellis",
    currency: "USD",
    totalAmount: 70,
    waitingSince: "2026-09-14T09:00:00Z",
    ...overrides,
  };
}

function agentInvoice(overrides: Partial<AgentInvoiceQueueItem> = {}): AgentInvoiceQueueItem {
  return {
    kind: "AGENT_INVOICE",
    id: "agent-invoice-1",
    status: "SENT",
    billingMonth: "2026-07-01",
    contractId: null,
    clientName: null,
    agentId: "agent-1",
    agentName: "Priya Nair",
    currency: "PHP",
    totalAmount: 2500,
    waitingSince: "2026-09-07T09:00:00Z",
    ...overrides,
  };
}

describe("ReviewQueueView", () => {
  it("shows each waiting invoice's type, subject, billing month, total and how long it has waited", () => {
    render(<ReviewQueueView items={[clientInvoice()]} now={NOW} />);

    const row = screen.getByRole("row", { name: /Aurora Retail Group/ });
    expect(within(row).getByText("Client Invoice")).toBeInTheDocument();
    expect(within(row).getByText("Aurora Retail Group — Jordan Ellis")).toBeInTheDocument();
    expect(within(row).getByText("August 2026")).toBeInTheDocument();
    expect(within(row).getByText("$70.00")).toBeInTheDocument();
    expect(within(row).getByText("3 days")).toBeInTheDocument();
  });

  it("links each row to that invoice's detail page", () => {
    render(
      <ReviewQueueView
        items={[
          clientInvoice(),
          clientInvoice({ id: "invoice-2", clientName: "Meridian Logistics", waitingSince: NOW }),
        ]}
        now={NOW}
      />,
    );

    const links = screen.getAllByRole("link", { name: /^Review/ });
    expect(links.map((link) => link.getAttribute("href"))).toEqual([
      "/manager/invoices/client/invoice-1",
      "/manager/invoices/client/invoice-2",
    ]);
    expect(screen.getByRole("link", { name: /Meridian Logistics/ })).toHaveAccessibleName(
      "Review Client Invoice: Meridian Logistics — Jordan Ellis, August 2026",
    );
  });

  it("says an invoice sent today has been waiting since today", () => {
    render(<ReviewQueueView items={[clientInvoice({ waitingSince: "2026-09-17T08:00:00Z" })]} now={NOW} />);

    expect(screen.getByText("Today")).toBeInTheDocument();
  });

  it("shows the empty state when nothing is waiting", () => {
    render(<ReviewQueueView items={[]} now={NOW} />);

    expect(screen.getByText("Nothing is waiting on you")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("shows an Agent Invoice row for its Agent, waiting for approval or payment, linking to its detail page", () => {
    render(
      <ReviewQueueView
        items={[agentInvoice(), agentInvoice({ id: "agent-invoice-2", agentName: "Mateo Ruiz", status: "APPROVED" })]}
        now={NOW}
      />,
    );

    const sent = screen.getByRole("row", { name: /Priya Nair/ });
    expect(within(sent).getByText("Agent Invoice")).toBeInTheDocument();
    expect(within(sent).getByText("July 2026")).toBeInTheDocument();
    expect(within(sent).getByText("10 days")).toBeInTheDocument();
    expect(within(sent).getByText("Approval")).toBeInTheDocument();
    expect(within(sent).getByRole("link")).toHaveAttribute("href", "/manager/invoices/agent/agent-invoice-1");
    expect(within(sent).getByRole("link")).toHaveAccessibleName("Review Agent Invoice: Priya Nair, July 2026");

    const approved = screen.getByRole("row", { name: /Mateo Ruiz/ });
    expect(within(approved).getByText("Payment")).toBeInTheDocument();
    expect(within(approved).getByRole("link")).toHaveAttribute("href", "/manager/invoices/agent/agent-invoice-2");
  });

  it("narrows the queue to one invoice type and back to all", async () => {
    render(<ReviewQueueView items={[clientInvoice(), agentInvoice()]} now={NOW} />);

    await userEvent.click(screen.getByRole("tab", { name: "Agent Invoices" }));
    expect(screen.getByRole("tab", { name: "Agent Invoices" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("row", { name: /Priya Nair/ })).toBeInTheDocument();
    expect(screen.queryByRole("row", { name: /Aurora Retail Group/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Client Invoices" }));
    expect(screen.getByRole("row", { name: /Aurora Retail Group/ })).toBeInTheDocument();
    expect(screen.queryByRole("row", { name: /Priya Nair/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "All" }));
    expect(screen.getByRole("row", { name: /Aurora Retail Group/ })).toBeInTheDocument();
    expect(screen.getByRole("row", { name: /Priya Nair/ })).toBeInTheDocument();
  });

  it("shows the empty state when the filter leaves nothing", async () => {
    render(<ReviewQueueView items={[clientInvoice()]} now={NOW} />);

    await userEvent.click(screen.getByRole("tab", { name: "Agent Invoices" }));

    expect(screen.getByText("Nothing is waiting on you")).toBeInTheDocument();
  });

  it("reflects the type filter in the URL and starts from the one it was opened with", async () => {
    const replaceState = vi.spyOn(window.history, "replaceState");
    render(<ReviewQueueView items={[clientInvoice(), agentInvoice()]} now={NOW} initialFilter="AGENT_INVOICE" />);

    expect(screen.getByRole("tab", { name: "Agent Invoices" })).toHaveAttribute("aria-selected", "true");
    expect(screen.queryByRole("row", { name: /Aurora Retail Group/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Client Invoices" }));
    expect(replaceState).toHaveBeenLastCalledWith(null, "", "?type=client");

    await userEvent.click(screen.getByRole("tab", { name: "All" }));
    expect(replaceState).toHaveBeenLastCalledWith(null, "", window.location.pathname);
  });
});
