import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ClientInvoiceDetail } from "@/lib/api/types";
import { AgentClientInvoicesView } from "./client-invoices-view";

const CONTRACT = { id: "contract-1", label: "Aurora Retail Group", currency: "USD" };

function invoice(overrides: Partial<ClientInvoiceDetail> = {}): ClientInvoiceDetail {
  return {
    id: "invoice-1",
    contractId: "contract-1",
    billingMonth: "2026-09-01",
    status: "DRAFT",
    currency: "USD",
    baseAmount: 0,
    basePostpaidSims: [],
    feeLines: [],
    totalAmount: 0,
    files: [],
    sentAt: null,
    approvedAt: null,
    ...overrides,
  };
}

describe("AgentClientInvoicesView", () => {
  it("lists a still-Active Postpaid SIM Card with no cancellation marker", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT]}
        invoicesByContract={{
          "contract-1": invoice({
            baseAmount: 25,
            totalAmount: 25,
            basePostpaidSims: [
              { simCardId: "sim-1", number: "+1-555-0100", monthlyFeeAmount: 25, cancellationEffectiveDate: null },
            ],
          }),
        }}
      />,
    );

    expect(screen.getByRole("heading", { name: "Postpaid SIM Cards" })).toBeInTheDocument();
    expect(screen.getByText("+1-555-0100")).toBeInTheDocument();
    expect(screen.queryByText(/Cancelled/)).not.toBeInTheDocument();
  });

  it("marks a cancelled SIM Card still billed this month with its cancellation date", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT]}
        invoicesByContract={{
          "contract-1": invoice({
            baseAmount: 18,
            totalAmount: 18,
            basePostpaidSims: [
              {
                simCardId: "sim-2",
                number: "+1-555-0201",
                monthlyFeeAmount: 18,
                cancellationEffectiveDate: "2026-09-15",
              },
            ],
          }),
        }}
      />,
    );

    expect(screen.getByText("+1-555-0201")).toBeInTheDocument();
    expect(screen.getByText(/Cancelled 2026-09-15/)).toBeInTheDocument();
  });

  it("shows no Postpaid SIM Cards section once the invoice is sent, with no live breakdown to show", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT]}
        invoicesByContract={{
          "contract-1": invoice({ status: "SENT", sentAt: "2026-09-10T10:00:00Z", basePostpaidSims: undefined }),
        }}
      />,
    );

    expect(screen.queryByRole("heading", { name: "Postpaid SIM Cards" })).not.toBeInTheDocument();
  });

  it("shows no Postpaid SIM Cards section when the Contract has none", () => {
    render(<AgentClientInvoicesView contracts={[CONTRACT]} invoicesByContract={{ "contract-1": invoice() }} />);

    expect(screen.queryByRole("heading", { name: "Postpaid SIM Cards" })).not.toBeInTheDocument();
  });
});
