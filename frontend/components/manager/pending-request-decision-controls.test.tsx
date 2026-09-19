import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { PendingRequestDecisionControls } from "./pending-request-decision-controls";

describe("PendingRequestDecisionControls", () => {
  it("approves with a single click, ending in a final Approved state", async () => {
    const fetchMock = stubFetch(200, { status: "SUBMITTED" });

    render(<PendingRequestDecisionControls requestId="request-1" />);
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/requests/request-1/approve", { method: "POST" });
    await waitFor(() => expect(screen.getByText("Approved")).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  it("shows a 409-specific message when the Request stopped being Pending Approval", async () => {
    stubFetch(409);

    render(<PendingRequestDecisionControls requestId="request-1" />);
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByText(/no longer Pending Approval/)).toBeInTheDocument();
    // The action is still available to retry, unlike the terminal Approved/Rejected state.
    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
  });

  it("shows a generic message on any other failure", async () => {
    stubFetch(500);

    render(<PendingRequestDecisionControls requestId="request-1" />);
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByText("Couldn't approve. Try again.")).toBeInTheDocument();
  });

  it("requires a reason before rejecting can be confirmed", async () => {
    const fetchMock = stubFetch(200, { status: "REJECTED" });

    render(<PendingRequestDecisionControls requestId="request-1" />);
    await userEvent.click(screen.getByRole("button", { name: "Reject" }));

    const confirm = screen.getByRole("button", { name: "Confirm reject" });
    await userEvent.click(confirm);
    expect(fetchMock).not.toHaveBeenCalled();

    await userEvent.type(screen.getByLabelText("Rejection reason"), "Budget is tight this month");
    await userEvent.click(confirm);

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(String(init.body))).toEqual({ reason: "Budget is tight this month" });
    await waitFor(() => expect(screen.getByText("Rejected")).toBeInTheDocument());
  });

  it("lets the Manager back out of rejecting without sending anything", async () => {
    const fetchMock = stubFetch(200, {});

    render(<PendingRequestDecisionControls requestId="request-1" />);
    await userEvent.click(screen.getByRole("button", { name: "Reject" }));
    await userEvent.type(screen.getByLabelText("Rejection reason"), "Some reason");
    await userEvent.click(screen.getByRole("button", { name: "Back" }));

    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("disables both actions while a decision is in flight", async () => {
    const { respond } = stubPendingFetch();

    render(<PendingRequestDecisionControls requestId="request-1" />);
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(screen.getByRole("button", { name: "Approve" })).toBeDisabled();

    respond(200, { status: "SUBMITTED" });
    await waitFor(() => expect(screen.getByText("Approved")).toBeInTheDocument());
  });
});
