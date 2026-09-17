import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
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

  it.each([409, 500])("shows an inline error and lets the Manager retry on a %i response", async (status) => {
    stubFetch(status);
    render(<ApproveClientInvoiceControl contractId="contract-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't approve. Try again.");
    expect(screen.getByRole("button", { name: "Approve" })).toBeEnabled();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });
});
