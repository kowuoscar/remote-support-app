import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { SimCardNumberControl } from "./sim-card-number-control";

function renderControl() {
  render(<SimCardNumberControl contractId="contract-1" simCardId="sim-1" number="+44 7700 900100" />);
}

describe("SimCardNumberControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("shows the number and an Edit button", () => {
    renderControl();

    expect(screen.getByText("+44 7700 900100")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit number, +44 7700 900100" })).toBeInTheDocument();
  });

  it("opens a field holding the current value", async () => {
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit number, +44 7700 900100" }));

    expect(screen.getByLabelText("SIM number")).toHaveValue("+44 7700 900100");
    expect(screen.getByLabelText("SIM number")).toHaveAttribute("inputmode", "tel");
  });

  it("sends the trimmed number, then leaves editing and refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit number, +44 7700 900100" }));
    await userEvent.clear(screen.getByLabelText("SIM number"));
    await userEvent.type(screen.getByLabelText("SIM number"), "  +44 7700 900123  ");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/sim-cards/sim-1/number",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ number: "+44 7700 900123" }) }),
    );

    respond(200, { id: "sim-1", number: "+44 7700 900123" });
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    expect(screen.queryByLabelText("SIM number")).not.toBeInTheDocument();
  });

  it("refuses a blank number without sending a request", async () => {
    const fetchMock = stubFetch(200);
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit number, +44 7700 900100" }));
    await userEvent.clear(screen.getByLabelText("SIM number"));
    await userEvent.type(screen.getByLabelText("SIM number"), "   ");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(screen.getByText("Enter a number.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows the length message for NUMBER_TOO_LONG and keeps the typed text", async () => {
    stubFetch(400, { code: "NUMBER_TOO_LONG", message: "too long" });
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit number, +44 7700 900100" }));
    await userEvent.type(screen.getByLabelText("SIM number"), " extra");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("A SIM number can be at most 64 characters.")).toBeInTheDocument();
    expect(screen.getByLabelText("SIM number")).toHaveValue("+44 7700 900100 extra");
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("shows the generic message for an unknown code and keeps the typed text", async () => {
    stubFetch(500, { code: "SOMETHING_ELSE" });
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit number, +44 7700 900100" }));
    await userEvent.type(screen.getByLabelText("SIM number"), "!");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText(/Couldn.t update\. Try again\./)).toBeInTheDocument();
    expect(screen.getByLabelText("SIM number")).toHaveValue("+44 7700 900100!");
  });

  it("Cancel restores the old value without a request", async () => {
    const fetchMock = stubFetch(200);
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit number, +44 7700 900100" }));
    await userEvent.type(screen.getByLabelText("SIM number"), "zzz");
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    expect(screen.getByText("+44 7700 900100")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("works by keyboard: Tab reaches Edit, Enter opens it, Enter saves", async () => {
    const fetchMock = stubFetch(200, { id: "sim-1", number: "+44 7700 900100" });
    renderControl();

    await userEvent.tab();
    expect(screen.getByRole("button", { name: "Edit number, +44 7700 900100" })).toHaveFocus();
    await userEvent.keyboard("{Enter}");
    expect(screen.getByLabelText("SIM number")).toHaveFocus();
    await userEvent.keyboard("{Enter}");

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
  });

  it("returns focus to Edit on Cancel and on Escape, and Escape sends nothing", async () => {
    const fetchMock = stubFetch(200);
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit number, +44 7700 900100" }));
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.getByRole("button", { name: "Edit number, +44 7700 900100" })).toHaveFocus();

    await userEvent.click(screen.getByRole("button", { name: "Edit number, +44 7700 900100" }));
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByLabelText("SIM number")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit number, +44 7700 900100" })).toHaveFocus();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("returns focus to Edit after a successful Save", async () => {
    stubFetch(200, {});
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit number, +44 7700 900100" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "Edit number, +44 7700 900100" })).toHaveFocus());
  });

  it("announces a refusal as an alert the field is described by", async () => {
    stubFetch(400, { code: "NUMBER_TOO_LONG" });
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit number, +44 7700 900100" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveClass("max-w-56");
    expect(screen.getByLabelText("SIM number")).toHaveAttribute("aria-describedby", alert.id);
  });
});
