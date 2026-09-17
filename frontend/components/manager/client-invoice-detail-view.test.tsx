import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { ClientInvoiceDetail } from "@/lib/api/types";
import { stubFetch } from "@/tests/component/fetch";
import { ClientInvoiceDetailView } from "./client-invoice-detail-view";

function invoice(overrides: Partial<ClientInvoiceDetail> = {}): ClientInvoiceDetail {
  return {
    id: "invoice-1",
    contractId: "contract-1",
    billingMonth: "2026-08-01",
    status: "SENT",
    currency: "USD",
    baseAmount: 25,
    feeLines: [
      {
        id: "fee-1",
        contractId: "contract-1",
        requestId: "request-1",
        requestType: "TOPUP",
        feeType: "TOPUP",
        amount: 45,
        currency: "USD",
        description: "Prepaid top-up",
        billingMonth: "2026-08-01",
        createdAt: "2026-08-12T10:00:00Z",
      },
    ],
    totalAmount: 70,
    files: [
      {
        id: "file-1",
        clientInvoiceId: "invoice-1",
        filename: "august-carrier.pdf",
        contentType: "application/pdf",
        sizeBytes: 1200,
        uploadedAt: "2026-08-30T10:00:00Z",
      },
    ],
    sentAt: "2026-08-31T18:00:00Z",
    approvedAt: null,
    ...overrides,
  };
}

describe("ClientInvoiceDetailView", () => {
  it("shows the full sent invoice with its files, PDF and the Approve action", () => {
    render(<ClientInvoiceDetailView invoice={invoice()} />);

    expect(screen.getByRole("heading", { name: "August 2026" })).toBeInTheDocument();
    expect(screen.getByText("Awaiting approval")).toBeInTheDocument();
    expect(screen.getByText("$25.00")).toBeInTheDocument();
    expect(screen.getByText("Prepaid top-up", { exact: false })).toBeInTheDocument();
    expect(screen.getByText("$70.00")).toBeInTheDocument();
    expect(screen.getByText(/Sent Aug 31, 2026/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Download august-carrier.pdf" })).toHaveAttribute(
      "href",
      "/api/client-invoices/invoice-1/files/file-1",
    );
    expect(screen.getByRole("link", { name: "Download PDF" })).toHaveAttribute(
      "href",
      "/api/client-invoices/invoice-1/pdf",
    );
    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
  });

  it("shows the final read-only state in place once approval succeeds", async () => {
    const fetchMock = stubFetch(200, invoice({ status: "APPROVED", approvedAt: "2026-09-17T09:30:00Z" }));
    render(<ClientInvoiceDetailView invoice={invoice()} />);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/client-invoices/invoice-1/approve", { method: "POST" });
    expect(await screen.findByText("Approved")).toBeInTheDocument();
    expect(screen.getByText(/Approved Sep 17, 2026/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.queryByText("Awaiting approval")).not.toBeInTheDocument();
  });

  it("keeps the invoice unchanged and shows an inline error when approval fails", async () => {
    stubFetch(409);
    render(<ClientInvoiceDetailView invoice={invoice()} />);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("Awaiting approval")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approve" })).toBeEnabled();
  });

  it.each([
    ["a draft", invoice({ status: "DRAFT", sentAt: null })],
    ["an approved", invoice({ status: "APPROVED", approvedAt: "2026-09-01T09:00:00Z" })],
  ])("renders %s invoice read-only, with no Approve action", (_label, readOnly) => {
    render(<ClientInvoiceDetailView invoice={readOnly} />);

    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  it("offers no PDF for a draft, which has nothing final to render", () => {
    render(<ClientInvoiceDetailView invoice={invoice({ status: "DRAFT", sentAt: null })} />);

    expect(screen.queryByRole("link", { name: "Download PDF" })).not.toBeInTheDocument();
  });
});
