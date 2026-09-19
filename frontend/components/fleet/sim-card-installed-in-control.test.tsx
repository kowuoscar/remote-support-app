import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { SimCardInstalledInControl } from "./sim-card-installed-in-control";

const SMARTPHONES = [
  { id: "phone-1", model: "iPhone 14", serial: "SN-1", status: "ACTIVE" as const },
  { id: "phone-2", model: "Pixel 8", serial: null, status: "ACTIVE" as const },
];

describe("SimCardInstalledInControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("shows 'Not installed' selected when the SIM Card has no link", () => {
    render(
      <SimCardInstalledInControl
        contractId="contract-1"
        simCardId="sim-1"
        smartphones={SMARTPHONES}
      />,
    );

    expect(screen.getByLabelText("Installed in")).toHaveValue("");
  });

  it("shows the current Smartphone selected when installed", () => {
    render(
      <SimCardInstalledInControl
        contractId="contract-1"
        simCardId="sim-1"
        installedInSmartphoneId="phone-1"
        smartphones={SMARTPHONES}
      />,
    );

    expect(screen.getByLabelText("Installed in")).toHaveValue("phone-1");
  });

  it("installs the SIM Card into the chosen Smartphone, then refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    render(
      <SimCardInstalledInControl
        contractId="contract-1"
        simCardId="sim-1"
        smartphones={SMARTPHONES}
      />,
    );

    await userEvent.selectOptions(screen.getByLabelText("Installed in"), "phone-2");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/sim-cards/sim-1/installed-in",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ smartphoneId: "phone-2" }) }),
    );

    respond(200, { id: "sim-1", installedInSmartphoneId: "phone-2" });
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
  });

  it("clears the link when 'Not installed' is chosen", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    render(
      <SimCardInstalledInControl
        contractId="contract-1"
        simCardId="sim-1"
        installedInSmartphoneId="phone-1"
        smartphones={SMARTPHONES}
      />,
    );

    await userEvent.selectOptions(screen.getByLabelText("Installed in"), "");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/sim-cards/sim-1/installed-in",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ smartphoneId: null }) }),
    );

    respond(200, { id: "sim-1" });
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
  });

  it("shows an inline error and lets the caller retry on failure", async () => {
    stubFetch(409);
    render(
      <SimCardInstalledInControl
        contractId="contract-1"
        simCardId="sim-1"
        smartphones={SMARTPHONES}
      />,
    );

    await userEvent.selectOptions(screen.getByLabelText("Installed in"), "phone-2");

    expect(await screen.findByText(/Couldn.t update\. Try again\./)).toBeInTheDocument();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });
});
