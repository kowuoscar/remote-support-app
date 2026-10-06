import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { clientInvoiceFromByIdResponse } from "@/lib/api/client-invoice";
import type { ClientInvoiceDetail } from "@/lib/api/types";
import { stubFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { AgentClientInvoicePageView, AgentClientInvoicesView } from "./client-invoices-view";

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

const SENT_BACK_ROW = {
  id: "invoice-9",
  contractId: "contract-2",
  clientName: "Birch Telecom",
  country: "KE",
  billingMonth: "2026-07-01",
  currency: "USD",
  sentBackAt: "2026-09-03T10:00:00Z",
  sentBackReason: "The July roaming Fee is missing its receipt, please attach it.",
};

describe("AgentClientInvoicesView sent back to you", () => {
  it("section-absent-when-list-is-empty", () => {
    render(
      <AgentClientInvoicesView contracts={[CONTRACT]} invoicesByContract={{ "contract-1": invoice() }} sentBack={[]} />,
    );
    expect(screen.queryByRole("heading", { name: "Sent back to you" })).not.toBeInTheDocument();
  });

  it("rows-show-contract-month-date-reason-and-open-link", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT]}
        invoicesByContract={{ "contract-1": invoice() }}
        sentBack={[SENT_BACK_ROW]}
      />,
    );
    const region = screen.getByRole("region", { name: "Sent back to you" });
    const row = within(region).getByRole("row", { name: /Birch Telecom/ });
    expect(within(row).getByText(/Birch Telecom/)).toBeInTheDocument();
    expect(within(row).getByText("July 2026")).toBeInTheDocument();
    expect(within(row).getByText(/Sep 3, 2026/)).toBeInTheDocument();
    expect(within(row).getByText(SENT_BACK_ROW.sentBackReason)).toBeInTheDocument();
    expect(within(row).getByRole("link", { name: /Open.*Birch Telecom.*July 2026/ })).toHaveAttribute(
      "href",
      "/agent/client-invoices/invoice-9",
    );
    // the section sits above the Contract switcher
    const switcher = screen.getByText("Aurora Retail Group");
    expect(region.compareDocumentPosition(switcher) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("current-month-card-shows-reason-and-badge-when-sent-back", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT]}
        invoicesByContract={{
          "contract-1": invoice({ sentBackAt: "2026-09-03T10:00:00Z", sentBackReason: "Fix the base amount." }),
        }}
      />,
    );
    expect(screen.getByText("Fix the base amount.")).toBeInTheDocument();
    expect(screen.getByText("Sent back")).toBeInTheDocument();
  });
});

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

  describe("the by-id page (send-a-client-invoice-back)", () => {
    const CURRENT_MONTH = "2026-10-01";
    const SIM = {
      simCardId: "sim-1",
      number: "+1-555-0100",
      monthlyFeeAmount: 25,
      cancellationEffectiveDate: null,
      amount: 25,
      computedAmount: 25,
      edited: false,
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
    const sentBack = (overrides: Partial<ClientInvoiceDetail> = {}) =>
      invoice({
        baseAmount: 25,
        totalAmount: 70,
        basePostpaidSims: [SIM],
        feeLines: [FEE],
        sentAt: "2026-09-10T10:00:00Z",
        sentBackAt: "2026-09-12T10:00:00Z",
        sentBackReason: "The top-up Fee is missing its receipt.",
        ...overrides,
      });
    const renderPage = (inv: ClientInvoiceDetail | null) =>
      render(<AgentClientInvoicePageView invoice={inv} currentBillingMonth={CURRENT_MONTH} />);

    beforeEach(() => {
      mockRouter.refresh.mockReset();
    });

    it("sent-back-badge-and-notice-with-correct-what-is-needed-line", () => {
      renderPage(sentBack());

      const notice = screen.getByRole("region", { name: "Sent back by the Manager on Sep 12, 2026" });
      expect(within(notice).getByText("The top-up Fee is missing its receipt.")).toBeInTheDocument();
      expect(within(notice).getByText("Correct what is needed, then send it again.")).toBeInTheDocument();
      expect(screen.getByText("Sent back")).toBeInTheDocument();
      expect(screen.queryByText("Draft")).not.toBeInTheDocument();
      expect(screen.getByRole("navigation", { name: "Breadcrumb" })).toHaveTextContent("Client Invoices");
    });

    it("a plain draft keeps its Draft badge and shows no notice", () => {
      renderPage(invoice({ billingMonth: CURRENT_MONTH }));

      expect(screen.getByText("Draft")).toBeInTheDocument();
      expect(screen.queryByText(/Sent back/)).not.toBeInTheDocument();
    });

    it("editor-present-on-sent-back-draft-and-calls-by-id-route", async () => {
      const fetchMock = stubFetch(200, {});
      renderPage(sentBack());

      expect(screen.getByRole("button", { name: /Attach carrier invoice/ })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Send Client Invoice" })).toBeInTheDocument();
      const row = screen.getByRole("row", { name: /\+1-555-0100/ });
      await userEvent.click(within(row).getByRole("button", { name: "Edit" }));
      await userEvent.clear(within(row).getByRole("textbox"));
      await userEvent.type(within(row).getByRole("textbox"), "31.40");
      await userEvent.click(within(row).getByRole("button", { name: "Save" }));

      await waitFor(() =>
        expect(fetchMock).toHaveBeenCalledWith(
          "/api/client-invoices/invoice-1/lines",
          expect.objectContaining({ method: "PUT" }),
        ),
      );
      expect(screen.getAllByRole("button", { name: "Edit" })).toHaveLength(2);
    });

    it("editor-attach-and-send-absent-on-past-month-draft-never-sent", () => {
      renderPage(sentBack({ sentAt: null, sentBackAt: null, sentBackReason: null }));

      expect(screen.queryByRole("button", { name: /Edit|Reset/ })).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /Attach carrier invoice/ })).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /Send/ })).not.toBeInTheDocument();
      expect(screen.getByRole("row", { name: /\+1-555-0100/ })).toHaveTextContent("$25.00");
    });

    it("offers no PDF for a past-month sent invoice, whose PDF isn't served by id", () => {
      renderPage(invoice({ status: "SENT", sentAt: "2026-09-10T10:00:00Z" }));

      expect(screen.queryByRole("link", { name: "Download PDF" })).not.toBeInTheDocument();
    });

    it("keeps the PDF link on a current-month sent invoice", () => {
      renderPage(invoice({ billingMonth: CURRENT_MONTH, status: "SENT", sentAt: "2026-10-02T10:00:00Z" }));

      expect(screen.getByRole("link", { name: "Download PDF" })).toBeInTheDocument();
    });

    it("a past-month sent-back draft isn't described as this month's", () => {
      renderPage(sentBack());

      expect(screen.queryByText(/this month’s Fees/)).not.toBeInTheDocument();
    });

    it("names the Client and country in the breadcrumb", () => {
      render(
        <AgentClientInvoicePageView
          invoice={sentBack()}
          currentBillingMonth={CURRENT_MONTH}
          contractLabel="Acme — Kenya"
        />,
      );

      expect(screen.getByRole("navigation", { name: "Breadcrumb" })).toHaveTextContent("Acme — Kenya · September 2026");
    });

    it("not found: honest copy and the breadcrumb back", () => {
      renderPage(null);

      expect(screen.getByText("It doesn't exist or isn't one of yours.")).toBeInTheDocument();
      expect(screen.getByRole("navigation", { name: "Breadcrumb" })).toHaveTextContent("Client Invoices");
    });

    it("a current-month draft never sent stays editable on its by-id page", () => {
      renderPage(invoice({ billingMonth: CURRENT_MONTH, basePostpaidSims: [SIM] }));

      expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Send Client Invoice" })).toBeInTheDocument();
    });

    it("send-confirmation-says-only-the-manager-can-send-it-back", async () => {
      renderPage(sentBack());

      await userEvent.click(screen.getByRole("button", { name: "Send Client Invoice" }));

      expect(
        screen.getByText("Sends to the Manager and Client, and locks the numbers. Only the Manager can send it back to you."),
      ).toBeInTheDocument();
      expect(screen.queryByText(/can.t be undone/)).not.toBeInTheDocument();
    });

    it("the current-month page shows the new confirmation copy too", async () => {
      render(<AgentClientInvoicesView contracts={[CONTRACT]} invoicesByContract={{ "contract-1": invoice() }} />);

      await userEvent.click(screen.getByRole("button", { name: "Send Client Invoice" }));

      expect(screen.getByText(/Only the Manager can send it back to you\./)).toBeInTheDocument();
    });

    it.each([
      ["another Agent's invoice id", 403],
      ["an unknown id", 404],
      ["another Tenant's id", 404],
    ])("by-id-page-shows-not-found-for-other-agent-unknown-and-other-tenant-id: %s", async (_name, status) => {
      const found = await clientInvoiceFromByIdResponse(new Response("{}", { status }));
      expect(found).toBeNull();

      renderPage(found);

      expect(screen.getByText("Client Invoice not found")).toBeInTheDocument();
      expect(screen.queryByText("Draft")).not.toBeInTheDocument();
    });

    it("the by-id read of an invoice of the Agent's own comes back as the invoice", async () => {
      const found = await clientInvoiceFromByIdResponse(new Response(JSON.stringify(sentBack()), { status: 200 }));
      expect(found?.id).toBe("invoice-1");
    });
  });
});
