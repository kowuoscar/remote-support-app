import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { MarkAgentInvoicePaidControl } from "./mark-agent-invoice-paid-control";

describe("MarkAgentInvoicePaidControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("marks the Agent Invoice paid on a single click, shows it pending, then refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    render(<MarkAgentInvoicePaidControl agentId="agent-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Mark paid" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/agents/agent-1/invoice/paid", { method: "POST" });
    expect(screen.getByRole("button", { name: "Mark paid" })).toBeDisabled();

    respond(200);

    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it.each([409, 500])("shows an inline error and lets the Manager retry on a %i response", async (status) => {
    stubFetch(status);
    render(<MarkAgentInvoicePaidControl agentId="agent-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Mark paid" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't mark as paid. Try again.");
    expect(screen.getByRole("button", { name: "Mark paid" })).toBeEnabled();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });
});
