import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { SendClientInvoiceControl } from "./send-client-invoice-control";

async function openConfirm() {
  await userEvent.click(screen.getByRole("button", { name: "Send Client Invoice" }));
}

describe("SendClientInvoiceControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("focuses Confirm when the confirm opens and the trigger again on Back", async () => {
    render(<SendClientInvoiceControl invoiceId="invoice-1" />);
    await openConfirm();
    expect(screen.getByRole("button", { name: "Confirm send" })).toHaveFocus();
    expect(
      screen.getByText("Sends to the Manager and Client, and locks the numbers. Only the Manager can send it back to you."),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Back" }));

    expect(screen.getByRole("button", { name: "Send Client Invoice" })).toHaveFocus();
  });

  it("closes the confirm on Escape and refocuses the trigger", async () => {
    render(<SendClientInvoiceControl invoiceId="invoice-1" />);
    await openConfirm();

    await userEvent.keyboard("{Escape}");

    expect(screen.queryByRole("button", { name: "Confirm send" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send Client Invoice" })).toHaveFocus();
  });

  it("stays focusable while sending (aria-busy, repeat activation ignored), then refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    render(<SendClientInvoiceControl invoiceId="invoice-1" />);
    await openConfirm();

    await userEvent.click(screen.getByRole("button", { name: "Confirm send" }));

    const busy = screen.getByRole("button", { name: "Sending…" });
    expect(busy).toHaveAttribute("aria-busy", "true");
    expect(busy).not.toBeDisabled();
    expect(busy).toHaveFocus();
    await userEvent.click(busy);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    respond(200, { id: "invoice-1" });
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
  });

  it("shows the failure and lets the Agent try again", async () => {
    stubFetch(500);
    render(<SendClientInvoiceControl invoiceId="invoice-1" />);
    await openConfirm();
    await userEvent.click(screen.getByRole("button", { name: "Confirm send" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't send. Try again.");
    expect(screen.getByRole("button", { name: "Confirm send" })).not.toHaveAttribute("aria-busy");
  });
});
