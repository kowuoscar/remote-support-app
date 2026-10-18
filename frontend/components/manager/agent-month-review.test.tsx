import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import type { ReviewClientInvoice, ReviewMonth, ReviewMonthSummary } from "@/lib/api/types";
import { stubFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { AgentMonthReview } from "./agent-month-review";

const AGENT = { agentId: "agent-1", agentName: "Avery Stone" };
const SENT_AT = "2026-10-12T12:00:00Z";

function row(overrides: Partial<ReviewClientInvoice> = {}): ReviewClientInvoice {
  return {
    id: "inv-1",
    contractId: "contract-1",
    clientName: "Aurora Retail Group",
    reviewStatus: "TO_REVIEW",
    currency: "USD",
    totalAmount: 1284.5,
    sentAt: SENT_AT,
    approvedAt: null,
    sentBackAt: null,
    ...overrides,
  };
}

function month(overrides: Partial<ReviewMonth> = {}): ReviewMonth {
  return {
    ...AGENT,
    billingMonth: "2026-10-01",
    monthSentAt: SENT_AT,
    clientInvoices: [row()],
    agentInvoice: null,
    ...overrides,
  };
}

const MONTHS: ReviewMonthSummary[] = [
  { billingMonth: "2026-10-01", waiting: 2 },
  { billingMonth: "2026-09-01", waiting: 0 },
];

function renderReview(review: ReviewMonth | null, months: ReviewMonthSummary[] | null = MONTHS, billingMonth = "2026-10-01") {
  return render(<AgentMonthReview {...AGENT} months={months} billingMonth={billingMonth} review={review} />);
}

beforeEach(() => {
  mockRouter.replace.mockReset();
});

describe("AgentMonthReview header", () => {
  it("reads how many of the month are still to review", () => {
    renderReview(
      month({
        clientInvoices: [
          row({ id: "a" }),
          row({ id: "b", clientName: "Birch Telecom", reviewStatus: "APPROVED" }),
          row({ id: "c", clientName: "Cedar Mobile", reviewStatus: "SENT_BACK" }),
        ],
      }),
    );

    const header = screen.getByRole("region", { name: "Month" });
    expect(within(header).getByRole("heading", { name: "October 2026" })).toBeInTheDocument();
    expect(header).toHaveTextContent("October 2026 · Sent to you on Oct 12, 2026 · 1 of 3 to review");
    // The read-only header of agents-side-list-on-invoices held no button; with one left to review it now holds the approval.
    expect(within(header).getByRole("button", { name: "Approve the 1 left to review" })).toBeInTheDocument();
  });

  it("reads how many are approved, and how many sent back, once nothing is left to review", () => {
    renderReview(
      month({
        clientInvoices: [
          row({ id: "a", reviewStatus: "APPROVED" }),
          row({ id: "b", clientName: "Birch Telecom", reviewStatus: "APPROVED" }),
          row({ id: "c", clientName: "Cedar Mobile", reviewStatus: "SENT_BACK" }),
        ],
      }),
    );

    expect(screen.getByRole("region", { name: "Month" })).toHaveTextContent(
      "October 2026 · Sent to you on Oct 12, 2026 · 2 of 3 approved · 1 sent back",
    );
  });

  it("leaves out the sent-back clause when none was sent back", () => {
    renderReview(month({ clientInvoices: [row({ reviewStatus: "APPROVED" })] }));

    const header = screen.getByRole("region", { name: "Month" });
    expect(header).toHaveTextContent("1 of 1 approved");
    expect(header).not.toHaveTextContent("sent back");
  });
});

describe("AgentMonthReview month approval", () => {
  it("offers the approval of every To review row in the header, and mounts the control with their ids", async () => {
    const fetchMock = stubFetch(200, {});
    renderReview(
      month({
        clientInvoices: [
          row({ id: "a" }),
          row({ id: "b", clientName: "Birch Telecom", reviewStatus: "APPROVED" }),
          row({ id: "c", clientName: "Cedar Mobile" }),
          row({ id: "d", clientName: "Dune Wireless", reviewStatus: "SENT_BACK" }),
        ],
      }),
    );
    const header = screen.getByRole("region", { name: "Month" });

    await userEvent.click(within(header).getByRole("button", { name: "Approve the 2 left to review" }));
    await userEvent.click(within(header).getByRole("button", { name: "Confirm approve 2" }));

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/agents/agent-1/client-invoice-months/2026-10/approve");
    expect(JSON.parse(init.body as string)).toEqual({ invoiceIds: ["a", "c"] });
  });

  it("holds no button when nothing is left to review", () => {
    renderReview(month({ clientInvoices: [row({ reviewStatus: "APPROVED" }), row({ id: "b", reviewStatus: "SENT_BACK" })] }));
    expect(within(screen.getByRole("region", { name: "Month" })).queryByRole("button")).not.toBeInTheDocument();
  });
});

describe("AgentMonthReview list", () => {
  it("lists one row per Client Invoice with its status, total and a Review link naming it", () => {
    renderReview(
      month({
        clientInvoices: [
          row({ id: "a", clientName: "Aurora Retail Group", totalAmount: 70 }),
          row({ id: "b", clientName: "Birch Telecom", reviewStatus: "APPROVED", currency: "GBP", totalAmount: 2310 }),
          row({ id: "c", clientName: "Cedar Mobile", reviewStatus: "SENT_BACK" }),
        ],
      }),
    );

    const aurora = screen.getByRole("row", { name: /Aurora Retail Group/ });
    expect(within(aurora).getByText("To review")).toBeInTheDocument();
    expect(within(aurora).getByText("$70.00")).toBeInTheDocument();
    expect(
      within(aurora).getByRole("link", { name: "Review Client Invoice: Aurora Retail Group — Avery Stone, October 2026" }),
    ).toHaveAttribute("href", "/manager/invoices/client/a");

    const birch = screen.getByRole("row", { name: /Birch Telecom/ });
    expect(within(birch).getByText("Approved")).toBeInTheDocument();
    expect(within(birch).getByText("£2,310.00")).toBeInTheDocument();
    expect(within(screen.getByRole("row", { name: /Cedar Mobile/ })).getByText("Sent back")).toBeInTheDocument();

    const clients = screen.getAllByRole("row").slice(1).map((r) => within(r).getAllByRole("cell")[0]?.textContent);
    expect(clients).toEqual(["Aurora Retail Group", "Birch Telecom", "Cedar Mobile"]);
  });
});

describe("AgentMonthReview Agent Invoice row", () => {
  it("shows the sent Agent Invoice under the table with its status, total and a Review link", () => {
    renderReview(month({ agentInvoice: { id: "ai-1", status: "SENT", currency: "USD", totalAmount: 3200 } }));

    const section = screen.getByRole("region", { name: "Agent Invoice" });
    expect(section).toHaveTextContent("Agent Invoice");
    expect(within(section).getByText("Awaiting approval")).toBeInTheDocument();
    expect(within(section).getByText("$3,200.00")).toBeInTheDocument();
    expect(
      within(section).getByRole("link", { name: "Review Agent Invoice: Avery Stone, October 2026" }),
    ).toHaveAttribute("href", "/manager/invoices/agent/ai-1");
  });

  it("has no row while the Agent Invoice is a draft", () => {
    renderReview(month({ agentInvoice: null }));

    expect(screen.queryByRole("region", { name: "Agent Invoice" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Review Agent Invoice/ })).not.toBeInTheDocument();
  });

  it("says no Client Invoice was sent when only the Agent Invoice was", () => {
    renderReview(
      month({
        monthSentAt: null,
        clientInvoices: [],
        agentInvoice: { id: "ai-1", status: "APPROVED", currency: "USD", totalAmount: 3200 },
      }),
    );

    expect(screen.getByRole("region", { name: "Month" })).toHaveTextContent("No Client Invoice sent for October 2026.");
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Review Agent Invoice: Avery Stone, October 2026" })).toBeInTheDocument();
  });
});

describe("AgentMonthReview Month select", () => {
  it("lists every sent month, newest first, with what waits in each", () => {
    renderReview(month());

    const select = screen.getByRole("combobox", { name: "Month" });
    expect(within(select).getAllByRole("option").map((o) => o.textContent)).toEqual([
      "October 2026 · 2 waiting",
      "September 2026",
    ]);
    expect(select).toHaveValue("2026-10");
  });

  it("writes the picked month to the address", async () => {
    renderReview(month());

    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Month" }), "2026-09");

    expect(mockRouter.replace).toHaveBeenCalledWith("/manager/invoices?agent=agent-1&month=2026-09", { scroll: false });
  });

  it("adds the shown month when the Agent never sent it", () => {
    renderReview(month({ billingMonth: "2025-01-01", monthSentAt: null, clientInvoices: [] }), MONTHS, "2025-01-01");

    const select = screen.getByRole("combobox", { name: "Month" });
    expect(within(select).getAllByRole("option").map((o) => o.textContent)).toEqual([
      "October 2026 · 2 waiting",
      "September 2026",
      "January 2025",
    ]);
    expect(select).toHaveValue("2025-01");
  });
});

describe("AgentMonthReview empty and failed states", () => {
  it("says an unsent month is unsent, with the select still there", () => {
    renderReview(month({ billingMonth: "2025-01-01", monthSentAt: null, clientInvoices: [] }), MONTHS, "2025-01-01");

    expect(screen.getByText("Avery Stone hasn't sent January 2025 yet.")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Month" })).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("says an Agent who never sent has no month, with no select", () => {
    renderReview(month({ clientInvoices: [], monthSentAt: null }), []);

    expect(screen.getByText("Avery Stone hasn't sent a month yet.")).toBeInTheDocument();
    expect(screen.getByText("Their Client Invoices appear here once they send their month.")).toBeInTheDocument();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("says the read failed when the month read did", () => {
    renderReview(null);

    expect(screen.getByText("Couldn't load Avery Stone's invoices.")).toBeInTheDocument();
    expect(screen.getByText("Refresh the page to try again.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("says the read failed when the months read did", () => {
    renderReview(null, null);

    expect(screen.getByText("Couldn't load Avery Stone's invoices.")).toBeInTheDocument();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });
});
