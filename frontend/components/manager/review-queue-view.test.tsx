import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ReviewQueueItem } from "@/lib/api/types";
import { ReviewQueueView } from "./review-queue-view";

const NOW = "2026-09-17T12:00:00Z";

function clientInvoice(overrides: Partial<ReviewQueueItem> = {}): ReviewQueueItem {
  return {
    kind: "CLIENT_INVOICE",
    id: "invoice-1",
    status: "SENT",
    billingMonth: "2026-08-01",
    contractId: "contract-1",
    clientName: "Aurora Retail Group",
    agentName: "Jordan Ellis",
    currency: "USD",
    totalAmount: 70,
    waitingSince: "2026-09-14T09:00:00Z",
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
});
