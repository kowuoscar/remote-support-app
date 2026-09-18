import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { SimCardListItem, SmartphoneListItem } from "@/lib/api/types";
import { SimSwapRequestDetails } from "./sim-swap-request-details";

function smartphone(id: string, model: string): SmartphoneListItem {
  return { id, contractId: "contract-1", model, serial: null, owner: "COMPANY", status: "ACTIVE" };
}

function simCard(
  id: string,
  number: string,
  installedInSmartphoneId?: string,
  installedInSmartphoneModel?: string,
): SimCardListItem {
  return {
    id,
    contractId: "contract-1",
    number,
    flavor: "PREPAID",
    monthlyFeeAmount: null,
    status: "ACTIVE",
    installedInSmartphoneId,
    installedInSmartphoneModel,
  };
}

describe("SimSwapRequestDetails", () => {
  it("defaults to Move mode, offering any Active SIM Card and any Active Smartphone", () => {
    render(
      <SimSwapRequestDetails
        smartphones={[smartphone("phone-1", "Pixel 8")]}
        simCards={[simCard("sim-1", "+1-555-0100")]}
        carriers={[]}
        carriersHref=""
        currency="USD"
      />,
    );

    expect(screen.getByRole("combobox", { name: "SIM Card to move" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Destination Smartphone" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: /\+1-555-0100/ })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Pixel 8" })).toBeInTheDocument();
  });

  it("in Exchange mode, the first picker offers only installed SIM Cards", async () => {
    const phoneA = smartphone("phone-a", "Pixel 8");
    const phoneB = smartphone("phone-b", "iPhone 15");
    render(
      <SimSwapRequestDetails
        smartphones={[phoneA, phoneB]}
        simCards={[
          simCard("sim-a", "+1-555-0100", "phone-a", "Pixel 8"),
          simCard("sim-b", "+1-555-0200", "phone-b", "iPhone 15"),
          simCard("sim-c", "+1-555-0300"),
        ]}
        carriers={[]}
        carriersHref=""
        currency="USD"
      />,
    );

    await userEvent.click(screen.getByRole("radio", { name: /Exchange the SIM Cards/ }));

    const firstPicker = screen.getByRole("combobox", { name: "First SIM Card" });
    expect(within(firstPicker).getByRole("option", { name: /\+1-555-0100/ })).toBeInTheDocument();
    expect(within(firstPicker).getByRole("option", { name: /\+1-555-0200/ })).toBeInTheDocument();
    expect(within(firstPicker).queryByRole("option", { name: /\+1-555-0300/ })).not.toBeInTheDocument();
  });

  it("the second picker only offers SIM Cards installed in a different Smartphone from the first", async () => {
    const phoneA = smartphone("phone-a", "Pixel 8");
    const phoneB = smartphone("phone-b", "iPhone 15");
    render(
      <SimSwapRequestDetails
        smartphones={[phoneA, phoneB]}
        simCards={[
          simCard("sim-a", "+1-555-0100", "phone-a", "Pixel 8"),
          simCard("sim-a2", "+1-555-0150", "phone-a", "Pixel 8"),
          simCard("sim-b", "+1-555-0200", "phone-b", "iPhone 15"),
        ]}
        carriers={[]}
        carriersHref=""
        currency="USD"
      />,
    );

    await userEvent.click(screen.getByRole("radio", { name: /Exchange the SIM Cards/ }));
    const firstPicker = screen.getByRole("combobox", { name: "First SIM Card" });
    await userEvent.selectOptions(firstPicker, within(firstPicker).getByRole("option", { name: /\+1-555-0100/ }));

    const secondPicker = screen.getByRole("combobox", { name: "Second SIM Card" });
    // The other SIM Card on the same Smartphone (phone-a) is excluded, and so is the first one
    // itself; only the SIM Card installed in the *other* Smartphone remains.
    expect(within(secondPicker).queryByRole("option", { name: /\+1-555-0150/ })).not.toBeInTheDocument();
    expect(within(secondPicker).queryByRole("option", { name: /\+1-555-0100/ })).not.toBeInTheDocument();
    expect(within(secondPicker).getByRole("option", { name: /\+1-555-0200/ })).toBeInTheDocument();
    expect(secondPicker).not.toBeDisabled();
  });

  it("disables the second picker until a first SIM Card is chosen", async () => {
    const phoneA = smartphone("phone-a", "Pixel 8");
    const phoneB = smartphone("phone-b", "iPhone 15");
    render(
      <SimSwapRequestDetails
        smartphones={[phoneA, phoneB]}
        simCards={[
          simCard("sim-a", "+1-555-0100", "phone-a", "Pixel 8"),
          simCard("sim-b", "+1-555-0200", "phone-b", "iPhone 15"),
        ]}
        carriers={[]}
        carriersHref=""
        currency="USD"
      />,
    );

    await userEvent.click(screen.getByRole("radio", { name: /Exchange the SIM Cards/ }));

    expect(screen.getByRole("combobox", { name: "Second SIM Card" })).toBeDisabled();
  });
});
