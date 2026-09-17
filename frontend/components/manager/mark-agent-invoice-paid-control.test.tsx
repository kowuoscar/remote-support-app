import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { MarkAgentInvoicePaidControl } from "./mark-agent-invoice-paid-control";

describe("MarkAgentInvoicePaidControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("marks the Agent Invoice paid by its id on a single click, shows it pending, then refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    render(<MarkAgentInvoicePaidControl invoiceId="invoice-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Mark paid" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/agent-invoices/invoice-1/paid", { method: "POST" });
    expect(screen.getByRole("button", { name: "Mark paid" })).toBeDisabled();

    respond(200);

    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows an inline error and lets the Manager retry on a 500 response", async () => {
    stubFetch(500);
    render(<MarkAgentInvoicePaidControl invoiceId="invoice-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Mark paid" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't mark as paid. Try again.");
    expect(screen.getByRole("button", { name: "Mark paid" })).toBeEnabled();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("tells the Manager to refresh when the invoice is no longer awaiting payment (409)", async () => {
    stubFetch(409);
    render(<MarkAgentInvoicePaidControl invoiceId="invoice-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Mark paid" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This invoice is no longer awaiting payment. Refresh to see its current status.",
    );
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("hands the paid invoice back", async () => {
    const paid = { id: "invoice-1", status: "PAID" };
    const fetchMock = stubFetch(200, paid);
    const onPaid = vi.fn();
    render(<MarkAgentInvoicePaidControl invoiceId="invoice-1" onPaid={onPaid} />);

    await userEvent.click(screen.getByRole("button", { name: "Mark paid" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/agent-invoices/invoice-1/paid", { method: "POST" });
    await waitFor(() => expect(onPaid).toHaveBeenCalledWith(paid));
  });
});
