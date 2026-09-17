import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { AgentInvoiceDetail } from "@/lib/api/types";
import { stubFetch } from "@/tests/component/fetch";
import { AgentInvoiceDetailView } from "./agent-invoice-detail-view";

function invoice(overrides: Partial<AgentInvoiceDetail> = {}): AgentInvoiceDetail {
  return {
    id: "invoice-1",
    agentId: "agent-1",
    billingMonth: "2026-08-01",
    status: "SENT",
    currency: "USD",
    localSupportFees: 320,
    salary: 2500,
    rolloutAdvanceRepayment: -200,
    rolloutAdvanceNewAdvance: 300,
    totalAmount: 2920,
    sentAt: "2026-08-31T18:00:00Z",
    approvedAt: null,
    paidAt: null,
    ...overrides,
  };
}

function line(name: string) {
  return screen.getByText(name).closest("div")!;
}

describe("AgentInvoiceDetailView", () => {
  it("shows every line, the total, status and timestamps of a sent invoice, with override and Approve", () => {
    render(<AgentInvoiceDetailView invoice={invoice()} />);

    expect(screen.getByRole("heading", { name: "August 2026" })).toBeInTheDocument();
    expect(screen.getByText("Awaiting approval")).toBeInTheDocument();
    expect(within(line("Local Support Fees")).getByText("$320.00")).toBeInTheDocument();
    expect(within(line("Salary")).getByText("$2,500.00")).toBeInTheDocument();
    expect(within(line("Rollout Advance repayment")).getByText("-$200.00")).toBeInTheDocument();
    expect(within(line("Rollout Advance new")).getByText("$300.00")).toBeInTheDocument();
    expect(screen.getByText("$2,920.00")).toBeInTheDocument();
    expect(screen.getByText(/Sent Aug 31, 2026/)).toBeInTheDocument();

    expect(screen.getByRole("spinbutton", { name: /Override Salary/ })).toBeInTheDocument();
    expect(screen.getByRole("spinbutton", { name: /Override Rollout Advance \(new\)/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Mark paid" })).not.toBeInTheDocument();
  });

  it("offers only Mark paid while approved", () => {
    render(<AgentInvoiceDetailView invoice={invoice({ status: "APPROVED", approvedAt: "2026-09-02T09:00:00Z" })} />);

    expect(screen.getByRole("button", { name: "Mark paid" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Override" })).not.toBeInTheDocument();
    expect(screen.getByText(/Approved Sep 2, 2026/)).toBeInTheDocument();
  });

  it.each([
    ["a draft", invoice({ status: "DRAFT", sentAt: null })],
    [
      "a paid",
      invoice({ status: "PAID", approvedAt: "2026-09-02T09:00:00Z", paidAt: "2026-09-05T09:00:00Z" }),
    ],
  ])("renders %s invoice read-only, with no actions", (_label, readOnly) => {
    render(<AgentInvoiceDetailView invoice={readOnly} />);

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.queryByRole("spinbutton")).not.toBeInTheDocument();
  });

  it("shows the overridden lines and total in place after an override", async () => {
    const fetchMock = stubFetch(200, invoice({ salary: 2750, totalAmount: 3170 }));
    render(<AgentInvoiceDetailView invoice={invoice()} />);
    const input = screen.getByRole("spinbutton", { name: /Override Salary/ });

    await userEvent.clear(input);
    await userEvent.type(input, "2750");
    await userEvent.click(within(input.closest("form")!).getByRole("button", { name: "Override" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/agent-invoices/invoice-1/override", expect.anything());
    expect(await screen.findByText("$3,170.00")).toBeInTheDocument();
    expect(within(line("Salary")).getByText("$2,750.00")).toBeInTheDocument();
  });

  it("moves to approved in place, then to its final paid state", async () => {
    stubFetch(200, invoice({ status: "APPROVED", approvedAt: "2026-09-17T09:30:00Z" }));
    render(<AgentInvoiceDetailView invoice={invoice()} />);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByRole("button", { name: "Mark paid" })).toBeInTheDocument();
    expect(screen.getByText("Approved")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.queryByRole("spinbutton")).not.toBeInTheDocument();

    stubFetch(
      200,
      invoice({ status: "PAID", approvedAt: "2026-09-17T09:30:00Z", paidAt: "2026-09-17T10:00:00Z" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "Mark paid" }));

    expect(await screen.findByText("Paid")).toBeInTheDocument();
    expect(screen.getByText(/Paid Sep 17, 2026/)).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("keeps the invoice unchanged and shows an inline error when approval fails", async () => {
    stubFetch(409);
    render(<AgentInvoiceDetailView invoice={invoice()} />);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("Awaiting approval")).toBeInTheDocument();
    expect(screen.getByText("$2,920.00")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approve" })).toBeEnabled();
  });
});
