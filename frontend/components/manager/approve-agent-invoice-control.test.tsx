import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { ApproveAgentInvoiceControl } from "./approve-agent-invoice-control";

describe("ApproveAgentInvoiceControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("approves the Agent Invoice on a single click, shows it pending, then refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    render(<ApproveAgentInvoiceControl agentId="agent-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/agents/agent-1/invoice/approve", { method: "POST" });
    expect(screen.getByRole("button", { name: "Approve" })).toBeDisabled();

    respond(200);

    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it.each([409, 500])("shows an inline error and lets the Manager retry on a %i response", async (status) => {
    stubFetch(status);
    render(<ApproveAgentInvoiceControl agentId="agent-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't approve. Try again.");
    expect(screen.getByRole("button", { name: "Approve" })).toBeEnabled();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });
});
