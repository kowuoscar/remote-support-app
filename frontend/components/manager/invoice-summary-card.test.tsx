import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { AgentInvoiceStatusValue, ClientInvoiceStatusValue } from "@/lib/api/types";
import { InvoiceSummaryCard } from "./invoice-summary-card";

function summary() {
  return screen.getByRole("region", { name: /Invoice$/ });
}

describe("InvoiceSummaryCard", () => {
  it.each<[ClientInvoiceStatusValue, string]>([
    ["DRAFT", "Draft"],
    ["SENT", "Awaiting approval"],
    ["APPROVED", "Approved"],
  ])("summarises a %s Client Invoice and links to its detail page", (status, statusLabel) => {
    render(
      <InvoiceSummaryCard
        kind="CLIENT_INVOICE"
        invoice={{ id: "invoice-1", billingMonth: "2026-09-01", status, totalAmount: 1234.5, currency: "USD" }}
      />,
    );

    const card = summary();
    expect(within(card).getByRole("heading", { name: "Client Invoice" })).toBeInTheDocument();
    expect(card).toHaveTextContent("September 2026");
    expect(within(card).getByText(statusLabel)).toBeInTheDocument();
    expect(card).toHaveTextContent("$1,234.50");
    expect(within(card).getByRole("link", { name: "Open invoice" })).toHaveAttribute(
      "href",
      "/manager/invoices/client/invoice-1",
    );
  });

  it.each<[AgentInvoiceStatusValue, string]>([
    ["DRAFT", "Draft"],
    ["SENT", "Awaiting approval"],
    ["APPROVED", "Approved"],
    ["PAID", "Paid"],
  ])("summarises a %s Agent Invoice and links to its detail page", (status, statusLabel) => {
    render(
      <InvoiceSummaryCard
        kind="AGENT_INVOICE"
        invoice={{ id: "invoice-2", billingMonth: "2026-08-01", status, totalAmount: 2500, currency: "EUR" }}
      />,
    );

    const card = summary();
    expect(within(card).getByRole("heading", { name: "Agent Invoice" })).toBeInTheDocument();
    expect(card).toHaveTextContent("August 2026");
    expect(within(card).getByText(statusLabel)).toBeInTheDocument();
    expect(card).toHaveTextContent("€2,500.00");
    expect(within(card).getByRole("link", { name: "Open invoice" })).toHaveAttribute(
      "href",
      "/manager/invoices/agent/invoice-2",
    );
  });

  it("offers no review action — only the link to the detail page", () => {
    render(
      <InvoiceSummaryCard
        kind="AGENT_INVOICE"
        invoice={{ id: "invoice-2", billingMonth: "2026-08-01", status: "SENT", totalAmount: 2500, currency: "EUR" }}
      />,
    );

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.queryByRole("spinbutton")).not.toBeInTheDocument();
  });
});
