import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { SendBackClientInvoiceControl } from "./send-back-client-invoice-control";

const URL = "/api/client-invoices/invoice-1/send-back";

async function openForm() {
  await userEvent.click(screen.getByRole("button", { name: "Send back" }));
}

describe("SendBackClientInvoiceControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("expand-and-back: Send back opens the reason form and Back closes it", async () => {
    render(<SendBackClientInvoiceControl endpoint={URL} />);
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();

    await openForm();

    const reason = screen.getByRole("textbox", { name: "Reason for sending back" });
    expect(reason).toHaveFocus();
    expect(
      screen.getByText(
        "Tell the Agent what is wrong or missing. They can change any line and attach files before sending it again.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Confirm send back" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Back" }));

    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send back" })).toHaveFocus();
  });

  it("Escape closes the form from the textarea and from a button", async () => {
    render(<SendBackClientInvoiceControl endpoint={URL} />);
    await openForm();
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();

    await openForm();
    screen.getByRole("button", { name: "Back" }).focus();
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("empty-reason-blocked-with-no-request", async () => {
    const fetchMock = stubFetch(200, {});
    render(<SendBackClientInvoiceControl endpoint={URL} />);
    await openForm();

    await userEvent.type(screen.getByRole("textbox"), "   ");
    await userEvent.click(screen.getByRole("button", { name: "Confirm send back" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Say why you are sending it back.");
    expect(screen.getByRole("textbox")).toHaveFocus();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("posts the trimmed reason as JSON, shows it pending, then refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    render(<SendBackClientInvoiceControl endpoint={URL} />);
    await openForm();

    await userEvent.type(screen.getByRole("textbox"), "  Wrong amount  ");
    await userEvent.click(screen.getByRole("button", { name: "Confirm send back" }));

    expect(fetchMock).toHaveBeenCalledWith(URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason: "Wrong amount" }),
    });
    const confirm = screen.getByRole("button", { name: "Sending back…" });
    expect(confirm).toHaveAttribute("aria-busy", "true");
    expect(confirm).not.toBeDisabled();
    await userEvent.click(confirm);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    respond(200, { id: "invoice-1" });
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
  });

  it("reports its form opening and closing", async () => {
    const onOpenChange = vi.fn();
    render(<SendBackClientInvoiceControl endpoint={URL} onOpenChange={onOpenChange} />);
    await openForm();
    expect(onOpenChange).toHaveBeenLastCalledWith(true);
    await userEvent.click(screen.getByRole("button", { name: "Back" }));
    expect(onOpenChange).toHaveBeenLastCalledWith(false);
  });

  it("409-shows-refresh-copy", async () => {
    stubFetch(409);
    render(<SendBackClientInvoiceControl endpoint={URL} />);
    await openForm();
    await userEvent.type(screen.getByRole("textbox"), "Wrong");
    await userEvent.click(screen.getByRole("button", { name: "Confirm send back" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This invoice is no longer awaiting approval. Refresh to see its current status.",
    );
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("generic-failure-keeps-reason", async () => {
    stubFetch(500);
    render(<SendBackClientInvoiceControl endpoint={URL} />);
    await openForm();
    await userEvent.type(screen.getByRole("textbox"), "Wrong amount");
    await userEvent.click(screen.getByRole("button", { name: "Confirm send back" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't send it back. Try again.");
    expect(screen.getByRole("textbox")).toHaveValue("Wrong amount");
    expect(screen.getByRole("button", { name: "Confirm send back" })).not.toHaveAttribute("aria-busy");
  });

  it("success-hands-invoice-to-callback", async () => {
    const draft = { id: "invoice-1", status: "DRAFT" };
    const fetchMock = stubFetch(200, draft);
    const onSentBack = vi.fn();
    render(<SendBackClientInvoiceControl endpoint={URL} onSentBack={onSentBack} />);
    await openForm();
    await userEvent.type(screen.getByRole("textbox"), "Wrong amount");
    await userEvent.click(screen.getByRole("button", { name: "Confirm send back" }));

    expect(fetchMock).toHaveBeenCalledOnce();
    await waitFor(() => expect(onSentBack).toHaveBeenCalledWith(draft));
  });
});
