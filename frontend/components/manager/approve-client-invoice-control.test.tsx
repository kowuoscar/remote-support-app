import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { ApproveClientInvoiceControl } from "./approve-client-invoice-control";

describe("ApproveClientInvoiceControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("approves the Contract's Client Invoice on a single click, shows it pending, then refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    render(<ApproveClientInvoiceControl contractId="contract-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/contracts/contract-1/client-invoice/approve", { method: "POST" });
    expect(screen.getByRole("button", { name: "Approve" })).toBeDisabled();

    respond(200);

    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows an inline error and lets the Manager retry on a 500 response", async () => {
    stubFetch(500);
    render(<ApproveClientInvoiceControl contractId="contract-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't approve. Try again.");
    expect(screen.getByRole("button", { name: "Approve" })).toBeEnabled();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("tells the Manager to refresh when the invoice is no longer awaiting approval (409)", async () => {
    stubFetch(409);
    render(<ApproveClientInvoiceControl contractId="contract-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This invoice is no longer awaiting approval. Refresh to see its current status.",
    );
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("approves a Client Invoice by its own id and hands the approved invoice back", async () => {
    const approved = { id: "invoice-1", status: "APPROVED" };
    const fetchMock = stubFetch(200, approved);
    const onApproved = vi.fn();
    render(<ApproveClientInvoiceControl invoiceId="invoice-1" onApproved={onApproved} />);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/client-invoices/invoice-1/approve", { method: "POST" });
    await waitFor(() => expect(onApproved).toHaveBeenCalledWith(approved));
  });
});
