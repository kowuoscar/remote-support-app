import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { SmartphoneModelControl } from "./smartphone-model-control";

function renderControl() {
  render(<SmartphoneModelControl contractId="contract-1" smartphoneId="phone-1" model="Pixle 8" />);
}

describe("SmartphoneModelControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("shows the model and an Edit button", () => {
    renderControl();

    expect(screen.getByText("Pixle 8")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
  });

  it("opens a field holding the current value", async () => {
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));

    expect(screen.getByLabelText("Smartphone model")).toHaveValue("Pixle 8");
  });

  it("sends the trimmed model, then leaves editing and refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.clear(screen.getByLabelText("Smartphone model"));
    await userEvent.type(screen.getByLabelText("Smartphone model"), "  Pixel 8 Pro  ");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/smartphones/phone-1/model",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ model: "Pixel 8 Pro" }) }),
    );

    respond(200, { id: "phone-1", model: "Pixel 8 Pro" });
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    expect(screen.queryByLabelText("Smartphone model")).not.toBeInTheDocument();
  });

  it("refuses a blank model without sending a request", async () => {
    const fetchMock = stubFetch(200);
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.clear(screen.getByLabelText("Smartphone model"));
    await userEvent.type(screen.getByLabelText("Smartphone model"), "   ");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(screen.getByText("Enter a model.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows the length message for MODEL_TOO_LONG and keeps the typed text", async () => {
    stubFetch(400, { code: "MODEL_TOO_LONG", message: "too long" });
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.type(screen.getByLabelText("Smartphone model"), " extra");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("A model can be at most 255 characters.")).toBeInTheDocument();
    expect(screen.getByLabelText("Smartphone model")).toHaveValue("Pixle 8 extra");
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("shows the generic message for an unknown code and keeps the typed text", async () => {
    stubFetch(500, { code: "SOMETHING_ELSE" });
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.type(screen.getByLabelText("Smartphone model"), "!");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText(/Couldn.t update\. Try again\./)).toBeInTheDocument();
    expect(screen.getByLabelText("Smartphone model")).toHaveValue("Pixle 8!");
  });

  it("Cancel restores the old value without a request", async () => {
    const fetchMock = stubFetch(200);
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.type(screen.getByLabelText("Smartphone model"), "zzz");
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    expect(screen.getByText("Pixle 8")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("works by keyboard: Tab reaches Edit, Enter opens it, Enter saves", async () => {
    const fetchMock = stubFetch(200, { id: "phone-1", model: "Pixle 8" });
    renderControl();

    await userEvent.tab();
    expect(screen.getByRole("button", { name: "Edit" })).toHaveFocus();
    await userEvent.keyboard("{Enter}");
    expect(screen.getByLabelText("Smartphone model")).toHaveFocus();
    await userEvent.keyboard("{Enter}");

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
  });
});
