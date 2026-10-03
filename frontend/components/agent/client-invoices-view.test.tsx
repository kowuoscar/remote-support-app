import { render, screen, within } from "@testing-library/react";
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
    // design-review finding on this feature's finisher pass: a bare LocalDate now renders through
    // formatLocalDate ("Sep 15, 2026"), not the raw ISO string.
    expect(screen.getByText(/Cancelled Sep 15, 2026/)).toBeInTheDocument();
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

  describe("editing lines (edit-client-invoice-lines)", () => {
    const EDITED_SIM = {
      simCardId: "sim-1",
      number: "+1-555-0100",
      monthlyFeeAmount: 25,
      cancellationEffectiveDate: null,
      amount: 31.4,
      computedAmount: 25,
      edited: true,
    };
    const FEE = {
      id: "fee-1",
      contractId: "contract-1",
      requestId: "request-1",
      requestType: "TOPUP" as const,
      feeType: "TOPUP" as const,
      amount: 45,
      currency: "USD",
      description: null,
      billingMonth: "2026-09-01",
      createdAt: "2026-09-02T10:00:00Z",
      computedAmount: 45,
      edited: false,
    };

    it("edited-line-shows-marker-and-reset: the SIM row shows its billed amount, not its monthly fee", () => {
      render(
        <AgentClientInvoicesView
          contracts={[CONTRACT]}
          invoicesByContract={{
            "contract-1": invoice({ baseAmount: 31.4, totalAmount: 31.4, basePostpaidSims: [EDITED_SIM] }),
          }}
        />,
      );

      const row = screen.getByRole("row", { name: /\+1-555-0100/ });
      expect(within(row).getByText("$31.40")).toBeInTheDocument();
      // The SIM's monthly fee shows only as the computed amount inside the marker.
      expect(within(row).getAllByText("$25.00")).toHaveLength(1);
      expect(within(row).getByText(/Edited · computed/)).toHaveTextContent("Edited · computed $25.00");
      expect(within(row).getByRole("button", { name: /Reset/ })).toBeInTheDocument();
      expect(within(row).getByRole("button", { name: "Edit" })).toBeInTheDocument();
    });

    it("gives each Postpaid SIM row and Fee row an Edit action on a draft", () => {
      render(
        <AgentClientInvoicesView
          contracts={[CONTRACT]}
          invoicesByContract={{
            "contract-1": invoice({
              baseAmount: 31.4,
              totalAmount: 76.4,
              basePostpaidSims: [EDITED_SIM],
              feeLines: [FEE],
            }),
          }}
        />,
      );

      expect(screen.getAllByRole("button", { name: "Edit" })).toHaveLength(2);
      expect(within(screen.getByRole("row", { name: /Topup/ })).getByRole("button", { name: "Edit" })).toBeInTheDocument();
    });

    it("F23: Fees of one type logged the same day with no description get distinct names; long descriptions are cut", () => {
      const long = "A".repeat(120);
      render(
        <AgentClientInvoicesView
          contracts={[CONTRACT]}
          invoicesByContract={{
            "contract-1": invoice({
              totalAmount: 100,
              feeLines: [
                FEE,
                { ...FEE, id: "fee-2" },
                { ...FEE, id: "fee-3", feeType: "REPLACE_SIM" as const, description: long },
              ],
            }),
          }}
        />,
      );

      const names = screen.getAllByRole("button", { name: "Edit" }).map((b) => b.getAttribute("aria-describedby"));
      const labels = names.map((id) => document.getElementById(id!)!.textContent!);
      expect(new Set(labels).size).toBe(3);
      expect(labels[0]).toContain("1 of 2");
      expect(labels[1]).toContain("2 of 2");
      expect(labels[2].length).toBeLessThan(90);
      expect(labels[2]).toContain("…");
    });

    it("no-edit-or-reset-on-sent-invoice, yet the marker stays", () => {
      render(
        <AgentClientInvoicesView
          contracts={[CONTRACT]}
          invoicesByContract={{
            "contract-1": invoice({
              status: "SENT",
              sentAt: "2026-09-10T10:00:00Z",
              baseAmount: 31.4,
              totalAmount: 76.4,
              basePostpaidSims: [EDITED_SIM],
              feeLines: [FEE],
            }),
          }}
        />,
      );

      expect(screen.queryByRole("button", { name: /Edit|Reset/ })).not.toBeInTheDocument();
      const row = screen.getByRole("row", { name: /\+1-555-0100/ });
      expect(within(row).getByText("$31.40")).toBeInTheDocument();
      expect(within(row).getByText(/Edited · computed/)).toHaveTextContent("Edited · computed $25.00");
    });

    it("hint-line-shown-on-draft, and not on a sent invoice", () => {
      const hint =
        "You can adjust any line to what was actually billed. Your Agent Invoice for this month follows these amounts, unless it is already approved.";
      const { rerender } = render(
        <AgentClientInvoicesView contracts={[CONTRACT]} invoicesByContract={{ "contract-1": invoice() }} />,
      );
      expect(screen.getByText(hint)).toBeInTheDocument();

      rerender(
        <AgentClientInvoicesView
          contracts={[CONTRACT]}
          invoicesByContract={{ "contract-1": invoice({ status: "SENT", sentAt: "2026-09-10T10:00:00Z" }) }}
        />,
      );
      expect(screen.queryByText(hint)).not.toBeInTheDocument();
    });
  });
});
