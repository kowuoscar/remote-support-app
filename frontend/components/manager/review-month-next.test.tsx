import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ReviewClientInvoice, ReviewMonth } from "@/lib/api/types";
import { ReviewMonthNext } from "./review-month-next";

function row(id: string, clientName: string, reviewStatus: ReviewClientInvoice["reviewStatus"]): ReviewClientInvoice {
  return {
    id,
    contractId: `contract-${id}`,
    clientName,
    reviewStatus,
    currency: "USD",
    totalAmount: 100,
    sentAt: "2026-10-12T12:00:00Z",
    approvedAt: null,
    sentBackAt: null,
  };
}

function month(clientInvoices: ReviewClientInvoice[]): ReviewMonth {
  return {
    agentId: "agent-1",
    agentName: "Avery Stone",
    billingMonth: "2026-10-01",
    monthSentAt: "2026-10-12T12:00:00Z",
    clientInvoices,
    agentInvoice: null,
  };
}

describe("ReviewMonthNext", () => {
  it("links to the next invoice still to review, named for its Client", () => {
    render(
      <ReviewMonthNext
        review={month([row("a", "Aurora", "TO_REVIEW"), row("b", "Birch Telecom", "TO_REVIEW")])}
        currentId="a"
      />,
    );

    const link = screen.getByRole("link", { name: "Next invoice: Birch Telecom" });
    expect(link).toHaveAttribute("href", "/manager/invoices/client/b");
    expect(link).toHaveTextContent("Next invoice");
  });

  it("says nothing else is left, with the month, when no other invoice is To review", () => {
    render(<ReviewMonthNext review={month([row("a", "Aurora", "TO_REVIEW"), row("b", "Birch", "APPROVED")])} currentId="a" />);

    expect(screen.getByText("Nothing else to review in October 2026.")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("renders nothing when the month could not be read", () => {
    const { container } = render(<ReviewMonthNext review={null} currentId="a" />);

    expect(container).toBeEmptyDOMElement();
  });
});
