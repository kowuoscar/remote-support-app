import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { SmartphoneOwnerControl } from "./smartphone-owner-control";

function renderControl() {
  render(<SmartphoneOwnerControl contractId="contract-1" smartphoneId="phone-1" owner="COMPANY" />);
}

describe("SmartphoneOwnerControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("shows the owner and an Edit button", () => {
    renderControl();

    expect(screen.getByText("Company")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit owner" })).toHaveTextContent("Edit");
  });

  it("opens a select holding the current owner", async () => {
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit owner" }));

    expect(screen.getByLabelText("Owner")).toHaveValue("COMPANY");
  });

  it("sends the chosen owner, then leaves editing and refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit owner" }));
    await userEvent.selectOptions(screen.getByLabelText("Owner"), "CLIENT");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/smartphones/phone-1/owner",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ owner: "CLIENT" }) }),
    );

    respond(200, { id: "phone-1", owner: "CLIENT" });
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    expect(screen.queryByLabelText("Owner")).not.toBeInTheDocument();
  });

  it("shows the open-Return message and keeps the choice", async () => {
    stubFetch(409, { code: "SMARTPHONE_ON_OPEN_RETURN", message: "open" });
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit owner" }));
    await userEvent.selectOptions(screen.getByLabelText("Owner"), "CLIENT");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(
      await screen.findByText("This smartphone is on an open Return. Change its owner once the Return is done."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Owner")).toHaveValue("CLIENT");
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("shows the generic message for an unknown code and keeps the choice", async () => {
    stubFetch(500, { code: "SOMETHING_ELSE" });
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit owner" }));
    await userEvent.selectOptions(screen.getByLabelText("Owner"), "CLIENT");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText(/Couldn.t update\. Try again\./)).toBeInTheDocument();
    expect(screen.getByLabelText("Owner")).toHaveValue("CLIENT");
  });

  it("Cancel restores the old owner without a request", async () => {
    const fetchMock = stubFetch(200);
    renderControl();

    await userEvent.click(screen.getByRole("button", { name: "Edit owner" }));
    await userEvent.selectOptions(screen.getByLabelText("Owner"), "CLIENT");
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    expect(screen.getByText("Company")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("works by keyboard: Tab reaches Edit, Enter opens it, Enter saves", async () => {
    const fetchMock = stubFetch(200, { id: "phone-1", owner: "COMPANY" });
    renderControl();

    await userEvent.tab();
    expect(screen.getByRole("button", { name: "Edit owner" })).toHaveFocus();
    await userEvent.keyboard("{Enter}");
    expect(screen.getByLabelText("Owner")).toHaveFocus();
    await userEvent.keyboard("{Enter}");

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
  });
});
