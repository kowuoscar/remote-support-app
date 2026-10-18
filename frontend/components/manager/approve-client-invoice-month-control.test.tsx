import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { ApproveClientInvoiceMonthControl } from "./approve-client-invoice-month-control";

function renderControl(invoiceIds = ["a", "b", "c"]) {
  return render(
    <ApproveClientInvoiceMonthControl
      agentId="agent-1"
      agentName="Avery Stone"
      billingMonth="2026-10-01"
      invoiceIds={invoiceIds}
    />,
  );
}

async function openConfirm(name = "Approve the 3 left to review") {
  await userEvent.click(screen.getByRole("button", { name }));
}

describe("ApproveClientInvoiceMonthControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("offers the approval of the many left to review", () => {
    renderControl();
    expect(screen.getByRole("button", { name: "Approve the 3 left to review" })).toBeEnabled();
  });

  it("offers the approval of the one left to review", () => {
    renderControl(["a"]);
    expect(screen.getByRole("button", { name: "Approve the 1 left to review" })).toBeEnabled();
  });

  it("opens the confirm on its Confirm button and returns focus to the trigger on Back", async () => {
    renderControl();
    await openConfirm();
    expect(screen.getByRole("button", { name: "Confirm approve 3" })).toHaveFocus();
    expect(
      screen.getByText(
        "Approves the 3 Client Invoices of Avery Stone for October 2026 still to review. An approved invoice is final.",
      ),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Back" }));

    expect(screen.getByRole("button", { name: "Approve the 3 left to review" })).toHaveFocus();
  });

  it("words the confirm for one invoice", async () => {
    renderControl(["a"]);
    await openConfirm("Approve the 1 left to review");
    expect(screen.getByRole("button", { name: "Confirm approve" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "Approves the Client Invoice of Avery Stone for October 2026 still to review. An approved invoice is final.",
      ),
    ).toBeInTheDocument();
  });

  it("posts the To review ids to the Agent's month route, then refreshes", async () => {
    const fetchMock = stubFetch(200, { approvedAt: "x" });
    renderControl();
    await openConfirm();

    await userEvent.click(screen.getByRole("button", { name: "Confirm approve 3" }));

    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/agents/agent-1/client-invoice-months/2026-10/approve");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({ invoiceIds: ["a", "b", "c"] });
  });

  it("stays focusable while approving (aria-busy, repeat activation ignored)", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    renderControl();
    await openConfirm();

    await userEvent.click(screen.getByRole("button", { name: "Confirm approve 3" }));

    const busy = screen.getByRole("button", { name: "Approving…" });
    expect(busy).toHaveAttribute("aria-busy", "true");
    expect(busy).not.toBeDisabled();
    await userEvent.click(busy);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    respond(200, {});
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
  });

  it.each([
    ["MONTH_CHANGED", "This month changed since the page loaded. Reload and try again."],
    ["NOTHING_TO_APPROVE", "Nothing is left to review. Reload to see the month."],
    ["SOMETHING_ELSE", "Couldn't approve. Try again."],
  ])("shows a refused approval (%s) under the confirm and lets the Manager retry", async (code, text) => {
    stubFetch(409, { code, message: "x" });
    renderControl();
    await openConfirm();

    await userEvent.click(screen.getByRole("button", { name: "Confirm approve 3" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(text);
    expect(screen.getByRole("button", { name: "Confirm approve 3" })).toBeInTheDocument();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("says so on a server error without a code", async () => {
    stubFetch(500);
    renderControl();
    await openConfirm();
    await userEvent.click(screen.getByRole("button", { name: "Confirm approve 3" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't approve. Try again.");
  });

  it("shows the network line when the request fails to reach the server", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("network")));
    renderControl();
    await openConfirm();
    await userEvent.click(screen.getByRole("button", { name: "Confirm approve 3" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Couldn't reach the server. Check your connection and try again.",
    );
  });
});
