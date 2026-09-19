import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import type { RequestListItem } from "@/lib/api/types";
import { PendingRequestDecisionControls } from "./pending-request-decision-controls";

function requestListItem(overrides: Partial<RequestListItem> = {}): RequestListItem {
  return {
    id: "request-1",
    contractId: "contract-1",
    type: "PROVISION_SMARTPHONE",
    status: "PENDING_APPROVAL",
    raisedByTesterId: "tester-1",
    raisedByUsername: "priya.raman@aurora.example",
    agentAuthored: false,
    loggedByUsername: "priya.raman@aurora.example",
    cancellationReason: null,
    description: null,
    createdAt: "2026-09-14T09:00:00Z",
    ...overrides,
  };
}

describe("PendingRequestDecisionControls", () => {
  it("approves with a single click, ending in a final Approved state", async () => {
    const fetchMock = stubFetch(200, { status: "SUBMITTED" });

    render(<PendingRequestDecisionControls request={requestListItem()} />);
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/requests/request-1/approve", { method: "POST" });
    await waitFor(() => expect(screen.getByText("Approved")).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  it("shows a 409-specific message when the Request stopped being Pending Approval", async () => {
    stubFetch(409);

    render(<PendingRequestDecisionControls request={requestListItem()} />);
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByText(/no longer Pending Approval/)).toBeInTheDocument();
    // The action is still available to retry, unlike the terminal Approved/Rejected state.
    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
  });

  it("shows a generic message on any other failure", async () => {
    stubFetch(500);

    render(<PendingRequestDecisionControls request={requestListItem()} />);
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(await screen.findByText("Couldn't approve. Try again.")).toBeInTheDocument();
  });

  it("requires a reason before rejecting can be confirmed", async () => {
    const fetchMock = stubFetch(200, { status: "REJECTED" });

    render(<PendingRequestDecisionControls request={requestListItem()} />);
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

    render(<PendingRequestDecisionControls request={requestListItem()} />);
    await userEvent.click(screen.getByRole("button", { name: "Reject" }));
    await userEvent.type(screen.getByLabelText("Rejection reason"), "Some reason");
    await userEvent.click(screen.getByRole("button", { name: "Back" }));

    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("disables both actions while a decision is in flight", async () => {
    const { respond } = stubPendingFetch();

    render(<PendingRequestDecisionControls request={requestListItem()} />);
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(screen.getByRole("button", { name: "Approve" })).toBeDisabled();

    respond(200, { status: "SUBMITTED" });
    await waitFor(() => expect(screen.getByText("Approved")).toBeInTheDocument());
  });

  // --- manager-decides-return-disposition ticket: Disposition pickers inside the approve control ---

  it("shows a Disposition picker only for a company-owned unit still missing one, preselected to its one fitting choice", () => {
    render(
      <PendingRequestDecisionControls
        request={requestListItem({
          type: "RETURN",
          returnedUnits: [
            { id: "unit-client-phone", smartphoneId: "phone-1", smartphoneModel: "Pixel 8", disposition: "POSTED_TO_CLIENT" },
            { id: "unit-company-phone", smartphoneId: "phone-2", smartphoneModel: "iPhone 15" },
            { id: "unit-sim", simCardId: "sim-1", simCardNumber: "+1-555-0100" },
          ],
        })}
      />,
    );

    // The already-decided Client-owned unit gets no picker.
    expect(screen.queryByText("Pixel 8")).not.toBeInTheDocument();

    expect(screen.getByText("iPhone 15")).toBeInTheDocument();
    expect(screen.getByLabelText(/iPhone 15/)).toHaveValue("POSTED_TO_COMPANY");
    expect(screen.getByText("+1-555-0100")).toBeInTheDocument();
    expect(screen.getByLabelText(/\+1-555-0100/)).toHaveValue("CANCELLED");
  });

  it("submits the chosen Dispositions on approve, keyed by each unit's own id", async () => {
    const fetchMock = stubFetch(200, { status: "SUBMITTED" });

    render(
      <PendingRequestDecisionControls
        request={requestListItem({
          type: "RETURN",
          returnedUnits: [
            { id: "unit-company-phone", smartphoneId: "phone-2", smartphoneModel: "iPhone 15" },
            { id: "unit-sim", simCardId: "sim-1", simCardNumber: "+1-555-0100" },
          ],
        })}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/requests/request-1/approve");
    expect(JSON.parse(String(init.body))).toEqual({
      dispositions: [
        { returnedUnitId: "unit-company-phone", disposition: "POSTED_TO_COMPANY" },
        { returnedUnitId: "unit-sim", disposition: "CANCELLED" },
      ],
    });
  });

  it("approves a Return with nothing left to choose with a single click, no body", async () => {
    const fetchMock = stubFetch(200, { status: "SUBMITTED" });

    render(
      <PendingRequestDecisionControls
        request={requestListItem({
          type: "RETURN",
          returnedUnits: [
            { id: "unit-client-phone", smartphoneId: "phone-1", smartphoneModel: "Pixel 8", disposition: "POSTED_TO_CLIENT" },
          ],
        })}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/requests/request-1/approve", { method: "POST" });
  });
});
