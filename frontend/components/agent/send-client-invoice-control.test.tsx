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

  describe("the carrier invoice gate", () => {
    it("is disabled with the singular note, linked by aria-describedby, while one Postpaid SIM lacks its file", () => {
      render(<SendClientInvoiceControl invoiceId="invoice-1" missingCarrierInvoices={1} />);

      const trigger = screen.getByRole("button", { name: "Send Client Invoice" });
      expect(trigger).toBeDisabled();
      const note = screen.getByText("1 Postpaid SIM still needs its carrier invoice.");
      expect(trigger).toHaveAttribute("aria-describedby", note.id);
    });

    it("counts several in the plural", () => {
      render(<SendClientInvoiceControl invoiceId="invoice-1" missingCarrierInvoices={3} />);

      expect(screen.getByText("3 Postpaid SIMs still need their carrier invoice.")).toBeInTheDocument();
    });

    it("is enabled, with no note and no description link, when none is missing", () => {
      render(<SendClientInvoiceControl invoiceId="invoice-1" missingCarrierInvoices={0} />);

      const trigger = screen.getByRole("button", { name: "Send Client Invoice" });
      expect(trigger).toBeEnabled();
      expect(trigger).not.toHaveAttribute("aria-describedby");
      expect(screen.queryByText(/carrier invoice\./)).not.toBeInTheDocument();
    });

    it("says to reload when the server refuses with CARRIER_INVOICES_MISSING", async () => {
      stubFetch(409, { code: "CARRIER_INVOICES_MISSING", message: "x" });
      render(<SendClientInvoiceControl invoiceId="invoice-1" />);
      await openConfirm();
      await userEvent.click(screen.getByRole("button", { name: "Confirm send" }));

      expect(await screen.findByRole("alert")).toHaveTextContent(
        "A Postpaid SIM still needs its carrier invoice. Reload to see which.",
      );
    });

    it("keeps the old text for another 409", async () => {
      stubFetch(409, { code: "PAST_MONTH_DRAFT_NOT_SENDABLE", message: "x" });
      render(<SendClientInvoiceControl invoiceId="invoice-1" />);
      await openConfirm();
      await userEvent.click(screen.getByRole("button", { name: "Confirm send" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't send. Try again.");
    });
  });
});
