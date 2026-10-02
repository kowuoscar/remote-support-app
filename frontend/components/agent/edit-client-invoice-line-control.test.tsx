import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { EditClientInvoiceLineControl } from "./edit-client-invoice-line-control";

function renderControl(overrides: { amount?: number; computedAmount?: number; edited?: boolean } = {}) {
  render(
    <EditClientInvoiceLineControl
      contractId="contract-1"
      kind="POSTPAID_SIM"
      sourceId="sim-1"
      label="SIM +1-555-0100"
      amount={25}
      computedAmount={25}
      edited={false}
      currency="USD"
      {...overrides}
    />,
  );
}

const amountField = () => screen.getByRole("textbox", { name: /Billed amount for SIM \+1-555-0100 \(USD\)/ });

describe("EditClientInvoiceLineControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("edit-opens-field-save-submits-and-cancel-closes", async () => {
    const fetchMock = stubFetch(200, {});
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    expect(amountField()).toHaveValue("25.00");

    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.clear(amountField());
    await userEvent.type(amountField(), "31.40");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/contracts/contract-1/client-invoice/lines", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ kind: "POSTPAID_SIM", sourceId: "sim-1", amount: "31.40" }),
    });
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("400-shows-inline-message-and-keeps-input", async () => {
    stubFetch(400, { message: "amount must not be negative" });
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.clear(amountField());
    await userEvent.type(amountField(), "-5");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("amount must not be negative");
    expect(amountField()).toHaveValue("-5");
    expect(amountField()).toBeInvalid();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("409-shows-refresh-copy", async () => {
    stubFetch(409);
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("This invoice was sent. Refresh to see it.");
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("generic-failure-shows-retry-message", async () => {
    stubFetch(500);
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't save. Try again.");
    expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();
  });

  it("edited-line-shows-marker-and-reset", async () => {
    const fetchMock = stubFetch(200, {});
    renderControl({ amount: 31.4, computedAmount: 25, edited: true });

    expect(screen.getByText("$31.40")).toBeInTheDocument();
    expect(screen.getByText(/Edited · computed/)).toHaveTextContent("Edited · computed $25.00");

    await userEvent.click(screen.getByRole("button", { name: "Reset" }));

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/client-invoice/lines",
      expect.objectContaining({ body: JSON.stringify({ kind: "POSTPAID_SIM", sourceId: "sim-1", amount: "25.00" }) }),
    );
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
  });

  it("an unedited line offers Edit but no marker and no Reset", () => {
    renderControl();

    expect(screen.queryByText(/Edited · computed/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reset" })).not.toBeInTheDocument();
  });

  it("a refused Save keeps focus in the field, which points at the error", async () => {
    stubFetch(400, { message: "amount must not be negative" });
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    const alert = await screen.findByRole("alert");
    await waitFor(() => expect(amountField()).toHaveFocus());
    expect(amountField()).toHaveAttribute("aria-describedby", alert.id);
    expect(amountField()).toHaveAttribute("name", "amount");
  });

  it("Escape closes the field and focus returns to Edit", async () => {
    const fetchMock = stubFetch(200, {});
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.keyboard("{Escape}");

    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit" })).toHaveFocus();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("Reset hands focus to the line's Edit button", async () => {
    stubFetch(200, {});
    renderControl({ amount: 31.4, computedAmount: 25, edited: true });

    await userEvent.click(screen.getByRole("button", { name: "Reset" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "Edit" })).toHaveFocus());
  });

  it("a refused Reset gives focus back to Reset", async () => {
    stubFetch(500);
    renderControl({ amount: 31.4, computedAmount: 25, edited: true });

    await userEvent.click(screen.getByRole("button", { name: "Reset" }));

    await screen.findByRole("alert");
    await waitFor(() => expect(screen.getByRole("button", { name: "Reset" })).toHaveFocus());
  });
});
