import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { clientInvoiceFromByIdResponse } from "@/lib/api/client-invoice";
import type { ClientInvoiceDetail } from "@/lib/api/types";
import { stubFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { AgentClientInvoicePageView, AgentClientInvoicesView } from "./client-invoices-view";

const CONTRACT = { id: "contract-1", clientName: "Aurora Retail Group", countryLabel: "Kenya", currency: "USD" };

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
    filesAvailable: true,
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
    const switcher = screen.getByText("Aurora Retail Group — Kenya");
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
    // The side list's row shows the same badge, so the card's is looked up inside the card.
    expect(within(screen.getByText("September 2026").parentElement!).getByText("Sent back")).toBeInTheDocument();
  });
});

const BIRCH = { id: "contract-2", clientName: "Birch Telecom", countryLabel: "Kenya", currency: "USD" };
const ZEPHYR = { id: "contract-3", clientName: "zephyr Mobile", countryLabel: "Ghana", currency: "USD" };

function clientsNav() {
  return screen.getByRole("navigation", { name: "Clients" });
}

function switcherTrigger() {
  return document.querySelector<HTMLElement>('[aria-haspopup="listbox"]')!;
}

describe("AgentClientInvoicesView side list (contract-side-list-on-desktop)", () => {
  const byContract = {
    "contract-1": invoice({ id: "invoice-1", baseAmount: 11, totalAmount: 11 }),
    "contract-2": invoice({ id: "invoice-2", contractId: "contract-2", baseAmount: 22, totalAmount: 22 }),
    "contract-3": invoice({ id: "invoice-3", contractId: "contract-3", baseAmount: 33, totalAmount: 33 }),
  };

  it("lists one link per Contract in alphabetical order whatever order props arrive in, the switcher alike", async () => {
    render(<AgentClientInvoicesView contracts={[ZEPHYR, BIRCH, CONTRACT]} invoicesByContract={byContract} />);

    const links = within(clientsNav()).getAllByRole("link");
    expect(links.map((l) => l.textContent)).toEqual(["Aurora Retail Group Draft", "Birch Telecom Draft", "zephyr Mobile Draft"]);

    await userEvent.click(switcherTrigger());
    const options = within(screen.getByRole("listbox")).getAllByRole("option");
    expect(options.map((o) => o.querySelector("span")?.textContent)).toEqual([
      "Aurora Retail Group — Kenya",
      "Birch Telecom — Kenya",
      "zephyr Mobile — Ghana",
    ]);
  });

  it("orders two Contracts of one Client by Country and shows the Country only on those rows", () => {
    const twin = { id: "contract-4", clientName: "Birch Telecom", countryLabel: "Ghana", currency: "USD" };
    render(
      <AgentClientInvoicesView
        contracts={[BIRCH, CONTRACT, twin]}
        invoicesByContract={{ ...byContract, "contract-4": invoice({ contractId: "contract-4" }) }}
      />,
    );

    const links = within(clientsNav()).getAllByRole("link");
    expect(links.map((l) => l.textContent)).toEqual(["Aurora Retail Group Draft", "Birch Telecom Ghana Draft", "Birch Telecom Kenya Draft"]);
    expect(within(clientsNav()).getByRole("link", { name: "Birch Telecom Ghana Draft" })).toBeInTheDocument();
    expect(within(links[0]).queryByText("Kenya")).not.toBeInTheDocument();
  });

  it("selects the first row on arrival and shows its invoice", () => {
    render(<AgentClientInvoicesView contracts={[ZEPHYR, BIRCH, CONTRACT]} invoicesByContract={byContract} />);

    const links = within(clientsNav()).getAllByRole("link");
    expect(links[0]).toHaveAttribute("aria-current", "true");
    expect(links[1]).not.toHaveAttribute("aria-current");
    expect(screen.getAllByText("$11.00").length).toBeGreaterThan(0);
    expect(screen.queryByText("$22.00")).not.toBeInTheDocument();
  });

  it("a click swaps the invoice in place, moves aria-current and makes no request", async () => {
    const fetchMock = stubFetch(200, {});
    render(<AgentClientInvoicesView contracts={[CONTRACT, BIRCH]} invoicesByContract={byContract} />);

    await userEvent.click(within(clientsNav()).getByRole("link", { name: "Birch Telecom Draft" }));

    const links = within(clientsNav()).getAllByRole("link");
    expect(links[1]).toHaveAttribute("aria-current", "true");
    expect(links[0]).not.toHaveAttribute("aria-current");
    expect(screen.getAllByText("$22.00").length).toBeGreaterThan(0);
    expect(screen.queryByText("$11.00")).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(mockRouter.push).not.toHaveBeenCalled();
  });

  it("each row's href names the Contract, so a modified click opens it", () => {
    render(<AgentClientInvoicesView contracts={[CONTRACT, BIRCH]} invoicesByContract={byContract} />);

    expect(within(clientsNav()).getByRole("link", { name: "Birch Telecom Draft" })).toHaveAttribute(
      "href",
      "/agent/client-invoices?contract=contract-2",
    );
  });

  it("a modified click is not intercepted", async () => {
    render(<AgentClientInvoicesView contracts={[CONTRACT, BIRCH]} invoicesByContract={byContract} />);
    const row = within(clientsNav()).getByRole("link", { name: "Birch Telecom Draft" });
    // jsdom does not navigate; what matters is that the view did not take the click for itself.
    let seen: MouseEvent | null = null;
    row.addEventListener("click", (e) => (seen = e));

    const user = userEvent.setup();
    await user.keyboard("{Control>}");
    await user.click(row);
    await user.keyboard("{/Control}");

    expect(seen!.ctrlKey).toBe(true);
    expect(seen!.defaultPrevented).toBe(false);
    expect(row).not.toHaveAttribute("aria-current");
  });

  it("Enter on a focused row selects it", async () => {
    render(<AgentClientInvoicesView contracts={[CONTRACT, BIRCH]} invoicesByContract={byContract} />);

    within(clientsNav()).getByRole("link", { name: "Birch Telecom Draft" }).focus();
    await userEvent.keyboard("{Enter}");

    expect(within(clientsNav()).getByRole("link", { name: "Birch Telecom Draft" })).toHaveAttribute("aria-current", "true");
  });

  it("the switcher choice moves the selected row too", async () => {
    render(<AgentClientInvoicesView contracts={[CONTRACT, BIRCH]} invoicesByContract={byContract} />);

    await userEvent.click(switcherTrigger());
    await userEvent.click(screen.getByRole("option", { name: /Birch Telecom/ }));

    expect(within(clientsNav()).getByRole("link", { name: "Birch Telecom Draft" })).toHaveAttribute("aria-current", "true");
  });

  it("a single Contract gives one selected row", () => {
    render(<AgentClientInvoicesView contracts={[CONTRACT]} invoicesByContract={byContract} />);

    const links = within(clientsNav()).getAllByRole("link");
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute("aria-current", "true");
  });

  it("no Contracts: 'No contracts yet' and no Clients landmark", () => {
    render(<AgentClientInvoicesView contracts={[]} invoicesByContract={{}} />);

    expect(screen.getByText("No contracts yet")).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Clients" })).not.toBeInTheDocument();
  });

  it("a 60-character name keeps its full name as the row's title", () => {
    const name = "Extraordinarily Long Client Name Incorporated Holdings Group";
    expect(name).toHaveLength(60);
    render(
      <AgentClientInvoicesView
        contracts={[{ ...CONTRACT, clientName: name }]}
        invoicesByContract={byContract}
      />,
    );

    expect(within(clientsNav()).getByText(name)).toHaveAttribute("title", name);
  });

  describe("the selected Client in the address", () => {
    beforeEach(() => window.history.replaceState(null, "", "/agent/client-invoices"));

    it("a valid initialContractId makes that row current", () => {
      render(
        <AgentClientInvoicesView
          contracts={[ZEPHYR, BIRCH, CONTRACT]}
          invoicesByContract={byContract}
          initialContractId="contract-2"
        />,
      );

      expect(within(clientsNav()).getByRole("link", { name: "Birch Telecom Draft" })).toHaveAttribute("aria-current", "true");
      expect(screen.getAllByText("$22.00").length).toBeGreaterThan(0);
    });

    it.each(["nonsense", "", undefined])("an unknown initialContractId (%s) opens the first row", (id) => {
      const spy = vi.spyOn(console, "error");
      render(
        <AgentClientInvoicesView
          contracts={[ZEPHYR, BIRCH, CONTRACT]}
          invoicesByContract={byContract}
          initialContractId={id}
        />,
      );

      expect(within(clientsNav()).getAllByRole("link")[0]).toHaveAttribute("aria-current", "true");
      expect(spy).not.toHaveBeenCalled();
      spy.mockRestore();
    });

    it("a row click writes ?contract=<id> without a history entry, a request or a router call", async () => {
      const fetchMock = stubFetch(200, {});
      const lengthBefore = window.history.length;
      render(<AgentClientInvoicesView contracts={[CONTRACT, BIRCH]} invoicesByContract={byContract} />);

      await userEvent.click(within(clientsNav()).getByRole("link", { name: "Birch Telecom Draft" }));

      expect(window.location.search).toBe("?contract=contract-2");
      expect(window.history.length).toBe(lengthBefore);
      expect(fetchMock).not.toHaveBeenCalled();
      expect(mockRouter.push).not.toHaveBeenCalled();
      expect(mockRouter.replace).not.toHaveBeenCalled();
    });

    it("a switcher choice writes ?contract=<id> too", async () => {
      const fetchMock = stubFetch(200, {});
      render(<AgentClientInvoicesView contracts={[CONTRACT, BIRCH]} invoicesByContract={byContract} />);

      await userEvent.click(switcherTrigger());
      await userEvent.click(screen.getByRole("option", { name: /Birch Telecom/ }));

      expect(window.location.search).toBe("?contract=contract-2");
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it("scrolls the selected row into the column on arrival", () => {
      const scrolled: number[] = [];
      const rect = vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
        if (this.tagName === "NAV") return { top: 0, bottom: 100, height: 100 } as DOMRect;
        if (this.getAttribute("aria-current")) return { top: 400, bottom: 440, height: 40 } as DOMRect;
        return { top: 0, bottom: 0, height: 0 } as DOMRect;
      });
      const original = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollTop");
      Object.defineProperty(HTMLElement.prototype, "scrollTop", {
        configurable: true,
        get: () => 0,
        set: (v: number) => void scrolled.push(v),
      });
      try {
        render(
          <AgentClientInvoicesView
            contracts={[CONTRACT, BIRCH]}
            invoicesByContract={byContract}
            initialContractId="contract-2"
          />,
        );
        expect(scrolled).toEqual([340]);
      } finally {
        rect.mockRestore();
        if (original) Object.defineProperty(HTMLElement.prototype, "scrollTop", original);
        else delete (HTMLElement.prototype as unknown as Record<string, unknown>).scrollTop;
      }
    });
  });

  it("scrolls the document to the top when the invoice area's top is above the viewport", async () => {
    const scrollTo = vi.spyOn(window, "scrollTo").mockImplementation(() => {});
    const rect = vi.spyOn(HTMLElement.prototype, "getBoundingClientRect");
    render(<AgentClientInvoicesView contracts={[CONTRACT, BIRCH]} invoicesByContract={byContract} />);

    rect.mockReturnValue({ top: 120 } as DOMRect);
    await userEvent.click(within(clientsNav()).getByRole("link", { name: "Birch Telecom Draft" }));
    expect(scrollTo).not.toHaveBeenCalled();

    rect.mockReturnValue({ top: -300 } as DOMRect);
    await userEvent.click(within(clientsNav()).getByRole("link", { name: "Aurora Retail Group Draft" }));
    expect(scrollTo).toHaveBeenCalledWith(expect.objectContaining({ top: 0 }));
    rect.mockRestore();
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

  it("reads each SIM line's number followed by its Carrier and 'Postpaid', and 'Postpaid' alone without a Carrier", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT]}
        invoicesByContract={{
          "contract-1": invoice({
            baseAmount: 50,
            totalAmount: 50,
            basePostpaidSims: [
              { simCardId: "sim-a", number: "+1-555-0301", monthlyFeeAmount: 25, cancellationEffectiveDate: null, carrierName: "Verizon" },
              { simCardId: "sim-b", number: "+1-555-0302", monthlyFeeAmount: 25, cancellationEffectiveDate: null, carrierName: null },
            ],
          }),
        }}
      />,
    );

    const first = screen.getByText("+1-555-0301").closest("tr") as HTMLElement;
    expect(within(first).getByText("Verizon · Postpaid")).toBeInTheDocument();
    const second = screen.getByText("+1-555-0302").closest("tr") as HTMLElement;
    expect(within(second).getByText("Postpaid")).toBeInTheDocument();
  });

  it("shows 'Carrier invoice attached' on a covered Postpaid SIM line and nothing on an uncovered one", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT]}
        invoicesByContract={{
          "contract-1": invoice({
            baseAmount: 50,
            totalAmount: 50,
            basePostpaidSims: [
              { simCardId: "sim-a", number: "+1-555-0301", monthlyFeeAmount: 25, cancellationEffectiveDate: null, carrierName: "Verizon", carrierInvoiceFileId: "file-a" },
              { simCardId: "sim-b", number: "+1-555-0302", monthlyFeeAmount: 25, cancellationEffectiveDate: null, carrierName: "Verizon", carrierInvoiceFileId: null },
            ],
          }),
        }}
      />,
    );

    const covered = screen.getByText("+1-555-0301").closest("tr") as HTMLElement;
    const mark = within(covered).getByRole("img", { name: "Carrier invoice attached" });
    const kind = within(covered).getByText("Verizon · Postpaid");
    expect(kind.compareDocumentPosition(mark) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const bare = screen.getByText("+1-555-0302").closest("tr") as HTMLElement;
    expect(within(bare).queryByRole("img", { name: "Carrier invoice attached" })).not.toBeInTheDocument();
    expect(screen.getAllByRole("img", { name: "Carrier invoice attached" })).toHaveLength(1);
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
    const SIM_TWO = { ...SIM, simCardId: "sim-2", number: "+1-555-0200" };
    // A line is covered when it names its file (FILE_OF_SIM_1): the line is the source of truth.
    const SIM_COVERED = { ...SIM, carrierInvoiceFileId: "file-1" };
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
        basePostpaidSims: [SIM_COVERED],
        feeLines: [FEE],
        // The send gate: the sent-back invoice's one line already has its carrier invoice.
        files: [FILE_OF_SIM_1],
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

    it("pauses-attach-and-download-with-a-notice-when-file-storage-is-unavailable", () => {
      renderPage(
        invoice({
          billingMonth: CURRENT_MONTH,
          basePostpaidSims: [SIM, SIM_TWO],
          filesAvailable: false,
          files: [
            {
              id: "file-1",
              clientInvoiceId: "invoice-1",
              simCardId: "sim-1",
              filename: "carrier.pdf",
              contentType: "application/pdf",
              sizeBytes: 10,
              uploadedAt: "2026-09-02T10:00:00Z",
              deletedAt: null,
            },
          ],
        }),
      );

      expect(screen.getByRole("button", { name: /Attach carrier invoice/ })).toBeDisabled();
      expect(screen.getByText("carrier.pdf")).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "Download" })).not.toBeInTheDocument();
      expect(
        screen.getByText("File storage is unavailable right now. Attaching, removing and downloading are paused."),
      ).toBeInTheDocument();
    });

    const FILE_OF_SIM_1 = {
      id: "file-1",
      clientInvoiceId: "invoice-1",
      simCardId: "sim-1",
      filename: "carrier.pdf",
      contentType: "application/pdf",
      sizeBytes: 10,
      uploadedAt: "2026-09-02T10:00:00Z",
      deletedAt: null,
    };

    it("offers Attach carrier invoice while a Postpaid SIM line has no file, and disables it once every line has one", () => {
      const { unmount } = renderPage(sentBack({ basePostpaidSims: [SIM_COVERED, SIM_TWO], files: [FILE_OF_SIM_1] }));
      expect(screen.getByRole("button", { name: /Attach carrier invoice/ })).toBeEnabled();
      unmount();

      renderPage(sentBack({ basePostpaidSims: [SIM_COVERED], files: [FILE_OF_SIM_1] }));
      expect(screen.getByRole("button", { name: /Attach carrier invoice/ })).toBeDisabled();
    });

    it("keeps a file whose SIM has no line listed, and still offers every line without its file", () => {
      renderPage(
        sentBack({ basePostpaidSims: [SIM], files: [{ ...FILE_OF_SIM_1, simCardId: "sim-gone" }] }),
      );

      expect(screen.getByText("carrier.pdf")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Attach carrier invoice/ })).toBeEnabled();
    });

    it("has no Attach carrier invoice when the invoice has no Postpaid SIM line", () => {
      renderPage(sentBack({ basePostpaidSims: [] }));
      expect(screen.queryByRole("button", { name: /Attach carrier invoice/ })).not.toBeInTheDocument();
      cleanup();

      renderPage(sentBack({ basePostpaidSims: undefined }));
      expect(screen.queryByRole("button", { name: /Attach carrier invoice/ })).not.toBeInTheDocument();
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

    describe("a deleted Carrier Invoice File (carrier-invoice-retention)", () => {
      const DELETED_FILE = { ...FILE_OF_SIM_1, deletedAt: "2026-10-02T12:00:00Z" };

      it("keeps the SIM line's 'Carrier invoice attached' check mark and lists the file as deleted", () => {
        renderPage(sentBack({ basePostpaidSims: [SIM_COVERED], files: [DELETED_FILE] }));

        const line = screen.getByText(SIM_COVERED.number).closest("tr") as HTMLElement;
        expect(within(line).getByRole("img", { name: "Carrier invoice attached" })).toBeInTheDocument();
        expect(screen.getByText("carrier.pdf")).toBeInTheDocument();
        expect(screen.getByText("Deleted on Oct 2, 2026")).toBeInTheDocument();
        expect(screen.queryByRole("link", { name: "Download" })).not.toBeInTheDocument();
      });

      it("still offers Remove on the deleted file of an editable draft", () => {
        renderPage(sentBack({ basePostpaidSims: [SIM_COVERED], files: [DELETED_FILE] }));

        expect(screen.getByRole("button", { name: "Remove carrier.pdf" })).toBeInTheDocument();
      });
    });

    describe("Remove (remove-carrier-invoice-file)", () => {
      const withFile = (overrides: Partial<ClientInvoiceDetail> = {}) =>
        sentBack({ basePostpaidSims: [SIM_COVERED, SIM_TWO], files: [FILE_OF_SIM_1], ...overrides });

      it("opens an inline confirm, sends a DELETE by invoice and file id, and refreshes the page", async () => {
        const fetchMock = stubFetch(204);
        renderPage(withFile());

        await userEvent.click(screen.getByRole("button", { name: "Remove carrier.pdf" }));
        expect(screen.getByText("Remove this file?")).toBeInTheDocument();
        expect(fetchMock).not.toHaveBeenCalled();
        await userEvent.click(screen.getByRole("button", { name: "Remove" }));

        await waitFor(() =>
          expect(fetchMock).toHaveBeenCalledWith(
            "/api/client-invoices/invoice-1/files/file-1",
            expect.objectContaining({ method: "DELETE" }),
          ),
        );
        await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalled());
      });

      it("Cancel closes the confirm without a request and gives the trigger its focus back", async () => {
        const fetchMock = stubFetch(204);
        renderPage(withFile());

        await userEvent.click(screen.getByRole("button", { name: "Remove carrier.pdf" }));
        await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

        expect(screen.queryByText("Remove this file?")).not.toBeInTheDocument();
        expect(fetchMock).not.toHaveBeenCalled();
        expect(screen.getByRole("button", { name: "Remove carrier.pdf" })).toHaveFocus();
      });

      it("shows a failure and keeps the file listed", async () => {
        stubFetch(500, {});
        renderPage(withFile());

        await userEvent.click(screen.getByRole("button", { name: "Remove carrier.pdf" }));
        await userEvent.click(screen.getByRole("button", { name: "Remove" }));

        expect(await screen.findByText("Couldn't remove the file. Try again.")).toBeInTheDocument();
        expect(screen.getByText("carrier.pdf")).toBeInTheDocument();
        expect(mockRouter.refresh).not.toHaveBeenCalled();
      });

      it("is disabled while file storage is unavailable, and the notice names removing", () => {
        renderPage(withFile({ filesAvailable: false }));

        expect(screen.getByRole("button", { name: "Remove carrier.pdf" })).toBeDisabled();
        // The secondary button's disabled look: half opacity, so a paused Remove does not read as live.
        expect(screen.getByRole("button", { name: "Remove carrier.pdf" })).toHaveClass("disabled:opacity-50");
        expect(
          screen.getByText("File storage is unavailable right now. Attaching, removing and downloading are paused."),
        ).toBeInTheDocument();
      });

      it("is absent on a sent invoice and on a past month's draft never sent", () => {
        renderPage(withFile({ status: "SENT" }));
        expect(screen.queryByRole("button", { name: /Remove/ })).not.toBeInTheDocument();
        cleanup();

        renderPage(withFile({ sentAt: null, sentBackAt: null, sentBackReason: null }));
        expect(screen.queryByRole("button", { name: /Remove/ })).not.toBeInTheDocument();
      });
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

    it("the breadcrumb returns to the list with this invoice's Client selected", () => {
      render(
        <AgentClientInvoicePageView
          invoice={sentBack()}
          currentBillingMonth={CURRENT_MONTH}
          contractLabel="Acme — Kenya"
        />,
      );

      expect(screen.getByRole("link", { name: "Client Invoices" })).toHaveAttribute(
        "href",
        "/agent/client-invoices?contract=contract-1",
      );
    });

    it("not found: the links stay plain", () => {
      renderPage(null);

      for (const link of screen.getAllByRole("link")) {
        expect(link).toHaveAttribute("href", "/agent/client-invoices");
      }
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

    describe("the send gate (carrier-invoice-per-sim)", () => {
      it("disables Send with a singular count note, linked by aria-describedby, while one line lacks its file", () => {
        renderPage(sentBack({ basePostpaidSims: [SIM_COVERED, SIM_TWO], files: [FILE_OF_SIM_1] }));

        const send = screen.getByRole("button", { name: "Send Client Invoice" });
        expect(send).toBeDisabled();
        const note = screen.getByText("1 Postpaid SIM still needs its carrier invoice.");
        expect(note).toHaveClass("max-w-65", "text-right", "text-label-sm", "text-ink-mute");
        expect(send).toHaveAccessibleDescription("1 Postpaid SIM still needs its carrier invoice.");
        expect(send.getAttribute("aria-describedby")).toBe(note.id);
      });

      it("counts several lines in the plural", () => {
        renderPage(sentBack({ basePostpaidSims: [SIM, SIM_TWO], files: [] }));

        expect(screen.getByRole("button", { name: "Send Client Invoice" })).toBeDisabled();
        expect(screen.getByText("2 Postpaid SIMs still need their carrier invoice.")).toBeInTheDocument();
      });

      it("enables Send with every line covered or with no Postpaid line, and shows no note", () => {
        const { unmount } = renderPage(sentBack({ basePostpaidSims: [SIM_COVERED], files: [FILE_OF_SIM_1] }));
        expect(screen.getByRole("button", { name: "Send Client Invoice" })).toBeEnabled();
        expect(screen.queryByText(/still needs? (its|their) carrier invoice/)).not.toBeInTheDocument();
        unmount();

        renderPage(sentBack({ basePostpaidSims: [], files: [] }));
        expect(screen.getByRole("button", { name: "Send Client Invoice" })).toBeEnabled();
      });

      it("does not count a file whose SIM has no line toward a line", () => {
        renderPage(sentBack({ basePostpaidSims: [SIM], files: [{ ...FILE_OF_SIM_1, simCardId: "sim-gone" }] }));

        expect(screen.getByRole("button", { name: "Send Client Invoice" })).toBeDisabled();
      });

      it("shows the reload message for a send refused with CARRIER_INVOICES_MISSING", async () => {
        stubFetch(409, { code: "CARRIER_INVOICES_MISSING", message: "1 Postpaid SIM line(s) lack their carrier invoice" });
        renderPage(sentBack());

        await userEvent.click(screen.getByRole("button", { name: "Send Client Invoice" }));
        await userEvent.click(screen.getByRole("button", { name: "Confirm send" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(
          "A Postpaid SIM still needs its carrier invoice. Reload to see which.",
        );
      });

      it("keeps the old text for any other failed send", async () => {
        stubFetch(409, { message: "Cannot send a Client Invoice from status SENT" });
        renderPage(sentBack());

        await userEvent.click(screen.getByRole("button", { name: "Send Client Invoice" }));
        await userEvent.click(screen.getByRole("button", { name: "Confirm send" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't send. Try again.");
      });
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

describe("AgentClientInvoicesView side list row status (side-list-row-status)", () => {
  const B = { id: "contract-2", clientName: "Birch Telecom", countryLabel: "Kenya", currency: "USD" };
  const Z = { id: "contract-3", clientName: "Zephyr Mobile", countryLabel: "Ghana", currency: "USD" };
  const Y = { id: "contract-4", clientName: "Yarrow Labs", countryLabel: "Ghana", currency: "USD" };

  it("each row's label names the month's status: Draft, Awaiting approval, Approved, Sent back", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT, B, Z, Y]}
        invoicesByContract={{
          "contract-1": invoice(),
          "contract-2": invoice({ contractId: "contract-2", status: "SENT", sentAt: "2026-09-10T10:00:00Z" }),
          "contract-3": invoice({ contractId: "contract-3", status: "APPROVED", approvedAt: "2026-09-11T10:00:00Z" }),
          "contract-4": invoice({ contractId: "contract-4", sentBackAt: "2026-09-03T10:00:00Z" }),
        }}
      />,
    );
    const nav = clientsNav();
    expect(within(nav).getByRole("link", { name: "Aurora Retail Group Draft" })).toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "Birch Telecom Awaiting approval" })).toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "Zephyr Mobile Approved" })).toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "Yarrow Labs Sent back" })).toBeInTheDocument();
  });

  it("a Contract whose invoice failed to load stays listed with no badge; selecting it explains, others still open", async () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT, B]}
        invoicesByContract={{ "contract-1": invoice({ baseAmount: 11, totalAmount: 11 }), "contract-2": null }}
      />,
    );
    const failed = within(clientsNav()).getByRole("link", { name: "Birch Telecom" });
    await userEvent.click(failed);

    expect(failed).toHaveAttribute("aria-current", "true");
    expect(screen.getByText("Couldn't load this Contract's Client Invoice")).toBeInTheDocument();
    expect(screen.getByText("Pick another Client or reload the page to try again.")).toBeInTheDocument();

    await userEvent.click(within(clientsNav()).getByRole("link", { name: "Aurora Retail Group Draft" }));
    expect(screen.getAllByText("$11.00").length).toBeGreaterThan(0);
  });
});

describe("AgentClientInvoicesView side list Sent back mark (sent-back-in-the-side-list)", () => {
  const B = { id: "contract-2", clientName: "Birch Telecom", countryLabel: "Kenya", currency: "USD" };
  const Z = { id: "contract-3", clientName: "Zephyr Mobile", countryLabel: "Ghana", currency: "USD" };
  const sentBackOf = (contractId: string, id = "invoice-9") => ({ ...SENT_BACK_ROW, id, contractId, country: "UNITED_KINGDOM" });
  const sentInvoice = (contractId: string) =>
    invoice({ contractId, status: "SENT", sentAt: "2026-09-10T10:00:00Z" });
  const rowNames = () => within(clientsNav()).getAllByRole("link").map((link) => link.textContent?.trim());

  it("an earlier-month sent-back invoice gives the month's status plus the mark; other rows show none", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT, B, Z]}
        invoicesByContract={{
          "contract-1": invoice(),
          "contract-2": sentInvoice("contract-2"),
          "contract-3": invoice({ contractId: "contract-3" }),
        }}
        sentBack={[sentBackOf("contract-2")]}
      />,
    );
    expect(within(clientsNav()).getByRole("link", { name: "Birch Telecom Awaiting approval Sent back" })).toBeInTheDocument();
    expect(within(clientsNav()).getAllByText("Sent back")).toHaveLength(1);
    expect(within(clientsNav()).getByRole("link", { name: "Aurora Retail Group Draft" })).toBeInTheDocument();
    expect(within(clientsNav()).getByRole("link", { name: "Zephyr Mobile Draft" })).toBeInTheDocument();
  });

  it("the mark carries the alert-triangle icon, not the status dot, so it differs from an Awaiting approval badge", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT, B]}
        invoicesByContract={{ "contract-1": invoice(), "contract-2": sentInvoice("contract-2") }}
        sentBack={[sentBackOf("contract-2")]}
      />,
    );
    const mark = within(clientsNav()).getByText("Sent back");
    expect(mark.querySelector("svg")).not.toBeNull();
    expect(mark.querySelector(".rounded-full")).toBeNull();
    const awaiting = within(clientsNav()).getByText("Awaiting approval");
    expect(awaiting.querySelector("svg")).toBeNull();
  });

  it("a this-month sent-back draft that is also in the list reads Sent back once", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT]}
        invoicesByContract={{ "contract-1": invoice({ sentBackAt: "2026-09-03T10:00:00Z", sentBackReason: "Fix it." }) }}
        sentBack={[sentBackOf("contract-1", "invoice-1")]}
      />,
    );
    expect(within(clientsNav()).getAllByText("Sent back")).toHaveLength(1);
    expect(within(clientsNav()).getByRole("link", { name: "Aurora Retail Group Sent back" })).toBeInTheDocument();
  });

  it("with no sent-back list, a this-month sent-back draft still reads Sent back once and no row has a mark", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT, B]}
        invoicesByContract={{
          "contract-1": invoice({ sentBackAt: "2026-09-03T10:00:00Z" }),
          "contract-2": sentInvoice("contract-2"),
        }}
        sentBack={[]}
      />,
    );
    expect(within(clientsNav()).getAllByText("Sent back")).toHaveLength(1);
    expect(rowNames()).toEqual(["Aurora Retail Group Sent back", "Birch Telecom Awaiting approval"]);
  });

  it("a failed-load row with an invoice in the list shows the mark alone", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT, B]}
        invoicesByContract={{ "contract-1": invoice(), "contract-2": null }}
        sentBack={[sentBackOf("contract-2")]}
      />,
    );
    expect(within(clientsNav()).getByRole("link", { name: "Birch Telecom Sent back" })).toBeInTheDocument();
    expect(within(clientsNav()).getByRole("link", { name: "Aurora Retail Group Draft" })).toBeInTheDocument();
  });

  it("the Sent back to you region precedes the invoice whichever row is selected", async () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT, B]}
        invoicesByContract={{ "contract-1": invoice(), "contract-2": sentInvoice("contract-2") }}
        sentBack={[sentBackOf("contract-2")]}
      />,
    );
    for (const name of [/^Aurora Retail Group/, /^Birch Telecom/]) {
      await userEvent.click(within(clientsNav()).getByRole("link", { name }));
      const region = screen.getByRole("region", { name: "Sent back to you" });
      const card = screen.getByTestId("client-invoice-card");
      expect(region.compareDocumentPosition(card) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    }
  });

  it("the Contract cell titles the full Client — Country and the Open link names the Country and month", () => {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT, B]}
        invoicesByContract={{ "contract-1": invoice(), "contract-2": invoice({ contractId: "contract-2" }) }}
        sentBack={[sentBackOf("contract-2")]}
      />,
    );
    const region = screen.getByRole("region", { name: "Sent back to you" });
    expect(within(region).getByTitle("Birch Telecom — United Kingdom")).toBeInTheDocument();
    expect(within(region).getByRole("link", { name: "Open Birch Telecom — United Kingdom, July 2026" })).toBeInTheDocument();
  });
});

describe("AgentClientInvoicesView invoice lines (invoice-lines-layout)", () => {
  const SIM = {
    simCardId: "sim-1",
    number: "+44 7700 900123",
    monthlyFeeAmount: 25,
    cancellationEffectiveDate: null,
    amount: 25,
    computedAmount: 25,
    edited: false,
  };
  const EDITED_SIM = { ...SIM, simCardId: "sim-2", number: "+44 7700 900456", amount: 30, edited: true };
  const CANCELLED_SIM = {
    ...SIM,
    simCardId: "sim-3",
    number: "+44 7700 900789",
    cancellationEffectiveDate: "2026-09-03",
  };
  const FEE = {
    id: "fee-1",
    contractId: "contract-1",
    requestId: "request-1",
    requestType: "TOPUP" as const,
    feeType: "TOPUP" as const,
    amount: 10,
    currency: "USD",
    description: "Customer asked for extra data",
    billingMonth: "2026-09-01",
    createdAt: "2026-09-12T10:00:00Z",
    computedAmount: 10,
    edited: false,
  };
  const EDITED_FEE = {
    ...FEE,
    id: "fee-2",
    feeType: "OTHER" as const,
    description: null,
    amount: 8,
    computedAmount: 12,
    edited: true,
    createdAt: "2026-09-14T10:00:00Z",
  };
  const LINES: Partial<ClientInvoiceDetail> = {
    baseAmount: 80,
    totalAmount: 98,
    basePostpaidSims: [SIM, EDITED_SIM, CANCELLED_SIM],
    feeLines: [FEE, EDITED_FEE],
  };

  function draft(overrides: Partial<ClientInvoiceDetail> = {}) {
    render(
      <AgentClientInvoicesView
        contracts={[CONTRACT]}
        invoicesByContract={{ "contract-1": invoice({ ...LINES, ...overrides }) }}
      />,
    );
  }

  it("one markup: a table per list, one row per line plus a header row, each amount and note once", () => {
    draft();

    const [sims, fees] = screen.getAllByRole("table");
    expect(screen.getAllByRole("table")).toHaveLength(2);
    expect(within(sims).getAllByRole("row")).toHaveLength(1 + 3);
    expect(within(fees).getAllByRole("row")).toHaveLength(1 + 2);
    expect(within(sims).getAllByRole("columnheader").map((h) => h.textContent)).toEqual(["Number", "Billed"]);
    expect(within(fees).getAllByRole("columnheader").map((h) => h.textContent)).toEqual([
      "Type",
      "Description",
      "Logged",
      "Billed",
    ]);
    const editedSim = within(sims).getByRole("row", { name: /900456/ });
    expect(within(editedSim).getAllByText("$30.00")).toHaveLength(1);
    expect(within(editedSim).getAllByRole("cell").length).toBeGreaterThan(0);
    expect(within(editedSim).getByRole("button", { name: "Reset" })).toBeInTheDocument();
    expect(screen.getAllByText(/Edited · computed/)).toHaveLength(2);
    // the plain and cancelled SIMs each bill $25.00, and the edited SIM shows it once, in its note
    expect(within(sims).getAllByText("$25.00")).toHaveLength(3);
    expect(within(editedSim).getAllByText("$25.00")).toHaveLength(1);
    expect(within(editedSim).getByText(/Edited · computed/)).toHaveTextContent("Edited · computed $25.00");
  });

  it("a Fee row says Logged with its date; a Fee with no description has no description text beyond the desktop dash", () => {
    draft();

    const withDescription = screen.getByRole("row", { name: /Topup/ });
    expect(within(withDescription).getByText(/Logged/)).toBeInTheDocument();
    expect(within(withDescription).getByText("Sep 12")).toBeInTheDocument();
    expect(within(withDescription).getByText("Customer asked for extra data")).toBeInTheDocument();

    const without = screen.getByRole("row", { name: /Other/ });
    expect(within(without).getByText(/Logged/)).toBeInTheDocument();
    expect(within(without).queryAllByText("—").length).toBeLessThanOrEqual(1);
    expect(without).not.toHaveTextContent(/Customer asked/);
  });

  it("a sent invoice keeps each edited line's note and has no button in any line row", () => {
    draft({ status: "SENT", sentAt: "2026-09-10T10:00:00Z" });

    for (const row of screen.getAllByRole("row")) {
      expect(within(row).queryByRole("button")).not.toBeInTheDocument();
    }
    expect(screen.getAllByText(/Edited · computed/)).toHaveLength(2);
  });

  it("a closed past-month draft keeps each edited line's note and has no button in any line row", () => {
    render(
      <AgentClientInvoicePageView
        invoice={invoice({ ...LINES, billingMonth: "2026-08-01" })}
        currentBillingMonth="2026-10-01"
      />,
    );

    for (const row of screen.getAllByRole("row")) {
      expect(within(row).queryByRole("button")).not.toBeInTheDocument();
    }
    expect(screen.getAllByText(/Edited · computed/)).toHaveLength(2);
  });
});
