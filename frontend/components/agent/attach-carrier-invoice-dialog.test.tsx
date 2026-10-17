import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import type { ClientInvoiceBaseSimLine } from "@/lib/api/types";
import { stubFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { AttachCarrierInvoiceFileControl } from "./attach-carrier-invoice-file-control";

function line(simCardId: string, number: string, carrierName: string | null): ClientInvoiceBaseSimLine {
  return { simCardId, number, monthlyFeeAmount: 25, cancellationEffectiveDate: null, carrierName };
}

const A = line("sim-a", "+1-202-555-0101", "Verizon");
const B = line("sim-b", "+1-202-555-0102", "T-Mobile");
const BARE = line("sim-c", "+1-202-555-0103", null);

async function openDialog(lines: ClientInvoiceBaseSimLine[]) {
  render(<AttachCarrierInvoiceFileControl invoiceId="invoice-1" missingLines={lines} />);
  await userEvent.click(screen.getByRole("button", { name: /Attach carrier invoice/ }));
  return await screen.findByRole("dialog", { name: "Attach carrier invoice" });
}

async function fillAndSubmit(dialog: HTMLElement, simLabel?: string) {
  if (simLabel) await userEvent.selectOptions(within(dialog).getByLabelText("Postpaid SIM"), simLabel);
  await userEvent.upload(
    within(dialog).getByLabelText("File"),
    new File(["bytes"], "bill.pdf", { type: "application/pdf" }),
  );
  await userEvent.click(within(dialog).getByRole("button", { name: "Attach" }));
}

describe("AttachCarrierInvoiceDialog", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("lists only the lines it is given, labelled '{number} · {Carrier}' or the bare number", async () => {
    const dialog = await openDialog([A, B, BARE]);

    const options = within(within(dialog).getByLabelText("Postpaid SIM")).getAllByRole("option");
    expect(options.map((o) => o.textContent)).toEqual([
      "Choose a Postpaid SIM",
      "+1-202-555-0101 · Verizon",
      "+1-202-555-0102 · T-Mobile",
      "+1-202-555-0103",
    ]);
    expect(within(dialog).getByLabelText("File")).not.toHaveAttribute("multiple");
  });

  it("preselects the line when exactly one is left", async () => {
    const dialog = await openDialog([B]);

    expect(within(dialog).getByLabelText("Postpaid SIM")).toHaveValue("sim-b");
  });

  it("posts the file and the chosen simCardId, then closes and refreshes the page", async () => {
    const fetchMock = stubFetch(201, { id: "file-1" });
    const dialog = await openDialog([A, B]);

    await fillAndSubmit(dialog, "+1-202-555-0102 · T-Mobile");

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/client-invoices/invoice-1/files");
    expect(init.method).toBe("POST");
    const body = init.body as FormData;
    expect(body.get("simCardId")).toBe("sim-b");
    expect((body.get("file") as File).name).toBe("bill.pdf");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(mockRouter.refresh).toHaveBeenCalled();
  });

  it.each([
    [409, { code: "CARRIER_INVOICE_ALREADY_ATTACHED", message: "x" }, "This SIM already has its carrier invoice. Remove it first to replace it."],
    [503, undefined, "File storage is unavailable right now. Try again later."],
    [500, undefined, "Couldn't attach the file. Try again."],
    [409, { code: "PAST_MONTH_DRAFT_NOT_SENDABLE", message: "x" }, "Couldn't attach the file. Try again."],
  ])("a %i answer shows its text and keeps the dialog open", async (status, body, text) => {
    stubFetch(status, body);
    const dialog = await openDialog([A]);

    await fillAndSubmit(dialog);

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(text);
    expect(screen.getByRole("dialog", { name: "Attach carrier invoice" })).toBeInTheDocument();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("asks for a file when none is chosen, and posts nothing", async () => {
    const fetchMock = stubFetch(201, {});
    const dialog = await openDialog([A]);

    await userEvent.click(within(dialog).getByRole("button", { name: "Attach" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Choose the file to attach.");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("is disabled when no line is missing its file, and while file storage is unavailable", () => {
    const { rerender } = render(<AttachCarrierInvoiceFileControl invoiceId="invoice-1" missingLines={[]} />);
    expect(screen.getByRole("button", { name: /Attach carrier invoice/ })).toBeDisabled();

    rerender(<AttachCarrierInvoiceFileControl invoiceId="invoice-1" missingLines={[A]} disabled />);
    expect(screen.getByRole("button", { name: /Attach carrier invoice/ })).toBeDisabled();
  });
});
