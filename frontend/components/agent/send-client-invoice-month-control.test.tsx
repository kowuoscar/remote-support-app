import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ClientInvoiceMonthState } from "@/lib/api/client-invoice-month";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { SendClientInvoiceMonthControl } from "./send-client-invoice-month-control";

function unsent(overrides: Partial<Extract<ClientInvoiceMonthState, { kind: "unsent" }>> = {}): ClientInvoiceMonthState {
  return {
    kind: "unsent",
    billingMonth: "2026-10-01",
    total: 3,
    ready: 3,
    invoiceIds: ["a", "b", "c"],
    ...overrides,
  };
}

async function openConfirm(name = "Send all 3 to Manager") {
  await userEvent.click(screen.getByRole("button", { name }));
}

describe("SendClientInvoiceMonthControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("reads the month and the count, with the button enabled when every invoice is ready", () => {
    render(<SendClientInvoiceMonthControl month={unsent()} />);
    expect(screen.getByText(/October 2026/)).toBeInTheDocument();
    expect(screen.getByText("3 of 3 ready")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send all 3 to Manager" })).toBeEnabled();
  });

  it("disables the button while any invoice is not ready and links the plural note to it", () => {
    render(<SendClientInvoiceMonthControl month={unsent({ ready: 1 })} />);
    const button = screen.getByRole("button", { name: "Send all 3 to Manager" });
    expect(button).toBeDisabled();
    expect(screen.getByText("1 of 3 ready")).toBeInTheDocument();
    expect(button).toHaveAccessibleDescription("2 invoices still need their carrier invoices.");
  });

  it("uses the singular note and the one-Contract wording", () => {
    render(<SendClientInvoiceMonthControl month={unsent({ total: 1, ready: 0, invoiceIds: ["a"] })} />);
    const button = screen.getByRole("button", { name: "Send 1 to Manager" });
    expect(button).toBeDisabled();
    expect(button).toHaveAccessibleDescription("1 invoice still needs its carrier invoices.");
  });

  it("opens the confirm on the Confirm button and returns focus to the trigger on Back", async () => {
    render(<SendClientInvoiceMonthControl month={unsent()} />);
    await openConfirm();
    expect(screen.getByRole("button", { name: "Confirm send all 3" })).toHaveFocus();
    expect(
      screen.getByText(
        "Sends all 3 Client Invoices to the Manager and the Clients, and locks their numbers. Only the Manager can send one back to you.",
      ),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Back" }));

    expect(screen.getByRole("button", { name: "Send all 3 to Manager" })).toHaveFocus();
  });

  it("keeps the month and the count on the left while the confirm is open", async () => {
    render(<SendClientInvoiceMonthControl month={unsent()} />);
    await openConfirm();
    expect(screen.getByText(/October 2026/)).toBeInTheDocument();
    expect(screen.getByText("3 of 3 ready")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Send all 3 to Manager" })).not.toBeInTheDocument();
  });

  it("words the confirm for one Contract", async () => {
    render(<SendClientInvoiceMonthControl month={unsent({ total: 1, ready: 1, invoiceIds: ["a"] })} />);
    await openConfirm("Send 1 to Manager");
    expect(screen.getByRole("button", { name: "Confirm send" })).toBeInTheDocument();
    expect(screen.getByText(/^Sends this Client Invoice to the Manager/)).toBeInTheDocument();
    expect(screen.queryByText(/Sends all 1/)).not.toBeInTheDocument();
  });

  it("posts the loaded invoice ids to the month's route, then refreshes", async () => {
    const fetchMock = stubFetch(200, { sent: true });
    render(<SendClientInvoiceMonthControl month={unsent()} />);
    await openConfirm();

    await userEvent.click(screen.getByRole("button", { name: "Confirm send all 3" }));

    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/me/client-invoice-months/2026-10/send");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({ invoiceIds: ["a", "b", "c"] });
  });

  it("stays focusable while sending (aria-busy, repeat activation ignored)", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    render(<SendClientInvoiceMonthControl month={unsent()} />);
    await openConfirm();

    await userEvent.click(screen.getByRole("button", { name: "Confirm send all 3" }));

    const busy = screen.getByRole("button", { name: "Sending…" });
    expect(busy).toHaveAttribute("aria-busy", "true");
    expect(busy).not.toBeDisabled();
    await userEvent.click(busy);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    respond(200, {});
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
  });

  it.each([
    ["CARRIER_INVOICES_MISSING", "A Postpaid SIM still needs its carrier invoice. Reload to see which."],
    ["MONTH_CHANGED", "Your Contracts changed since this page loaded. Reload and try again."],
    ["MONTH_ALREADY_SENT", "This month was already sent. Reload to see it."],
    ["NOT_THE_CURRENT_MONTH", "The month has changed. Reload to see this month's invoices."],
    ["SOMETHING_ELSE", "Couldn't send. Try again."],
  ])("shows a refused send (%s) under the confirm and lets the Agent retry", async (code, text) => {
    stubFetch(409, { code, message: "x" });
    render(<SendClientInvoiceMonthControl month={unsent()} />);
    await openConfirm();

    await userEvent.click(screen.getByRole("button", { name: "Confirm send all 3" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(text);
    expect(screen.getByRole("button", { name: "Confirm send all 3" })).toBeInTheDocument();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("says so on a server error without a code", async () => {
    stubFetch(500);
    render(<SendClientInvoiceMonthControl month={unsent()} />);
    await openConfirm();
    await userEvent.click(screen.getByRole("button", { name: "Confirm send all 3" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't send. Try again.");
  });

  it("shows the network line when the request fails to reach the server", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("network")));
    render(<SendClientInvoiceMonthControl month={unsent()} />);
    await openConfirm();
    await userEvent.click(screen.getByRole("button", { name: "Confirm send all 3" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Couldn't reach the server. Check your connection and try again.",
    );
  });

  it("shows the sent bar with the date and no button", () => {
    render(
      <SendClientInvoiceMonthControl
        month={{ kind: "sent", billingMonth: "2026-10-01", sentAt: "2026-10-02T09:00:00Z" }}
      />,
    );
    expect(screen.getByText(/October 2026/)).toBeInTheDocument();
    expect(screen.getByText(/Sent to Manager on Oct 2, 2026/)).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("shows the incomplete bar with the button disabled and its note", () => {
    render(<SendClientInvoiceMonthControl month={{ kind: "incomplete", billingMonth: "2026-10-01" }} />);
    expect(screen.getByText("Couldn't load every invoice.")).toBeInTheDocument();
    const button = screen.getByRole("button", { name: /^Send/ });
    expect(button).toBeDisabled();
    expect(button).toHaveAccessibleDescription("Reload the page to send the month.");
  });
});
