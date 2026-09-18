import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { SmartphoneSerialControl } from "./smartphone-serial-control";

describe("SmartphoneSerialControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("shows '—' and a 'Set serial' action when there is none yet", () => {
    render(<SmartphoneSerialControl contractId="contract-1" smartphoneId="phone-1" serial={null} />);

    expect(screen.getByText("—")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Set serial" })).toBeInTheDocument();
  });

  it("shows the current serial and an 'Edit' action when one is set", () => {
    render(<SmartphoneSerialControl contractId="contract-1" smartphoneId="phone-1" serial="SN-1" />);

    expect(screen.getByText("SN-1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
  });

  it("sets the serial, then refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    render(<SmartphoneSerialControl contractId="contract-1" smartphoneId="phone-1" serial={null} />);

    await userEvent.click(screen.getByRole("button", { name: "Set serial" }));
    await userEvent.type(screen.getByLabelText("Smartphone serial"), "SN-NEW-1");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/smartphones/phone-1/serial",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ serial: "SN-NEW-1" }) }),
    );

    respond(200, { id: "phone-1", serial: "SN-NEW-1" });
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
  });

  it("shows an inline error and lets the caller retry on failure", async () => {
    stubFetch(500);
    render(<SmartphoneSerialControl contractId="contract-1" smartphoneId="phone-1" serial="SN-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText(/Couldn.t update\. Try again\./)).toBeInTheDocument();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("cancels back to the read-only view without saving", async () => {
    render(<SmartphoneSerialControl contractId="contract-1" smartphoneId="phone-1" serial="SN-1" />);

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.clear(screen.getByLabelText("Smartphone serial"));
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    expect(screen.getByText("SN-1")).toBeInTheDocument();
    expect(screen.queryByLabelText("Smartphone serial")).not.toBeInTheDocument();
  });
});
