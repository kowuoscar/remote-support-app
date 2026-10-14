import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import type { CarrierOfferItem, CatalogCarrierItem } from "@/lib/api/types";
import { SimCardPlanControl } from "./sim-card-plan-control";

function plan(name: string, price: number, archivedAt: string | null = null): CarrierOfferItem {
  return { id: `plan-${name}`, carrierId: "carrier-1", name, price, archivedAt };
}

const carrier: CatalogCarrierItem = {
  id: "carrier-1",
  country: "UNITED_STATES",
  name: "Verizon",
  archivedAt: null,
  topupOptions: [],
  postpaidPlans: [plan("Welcome", 65), plan("Plus", 80), plan("Legacy", 70, "2024-06-01T00:00:00Z")],
};

function renderControl() {
  render(
    <SimCardPlanControl
      contractId="contract-1"
      simCardId="sim-1"
      sim={{ postpaidPlanId: "plan-Welcome", postpaidPlanName: "Welcome", postpaidPlanArchived: false }}
      carrier={carrier}
      currency="USD"
      carriersHref="/manager/carriers"
    />,
  );
}

async function openEditor() {
  await userEvent.click(screen.getByRole("button", { name: "Edit plan" }));
}

describe("SimCardPlanControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("shows the plan's name and an Edit plan button", () => {
    renderControl();

    expect(screen.getByText("Welcome")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit plan" })).toBeInTheDocument();
  });

  it("offers only the active plans of the SIM's Carrier, holding the current one", async () => {
    renderControl();

    await openEditor();

    expect(screen.getAllByRole("option").map((option) => option.textContent)).toEqual([
      "Choose a plan",
      "Welcome",
      "Plus",
    ]);
    expect(screen.getByRole("combobox", { name: "Postpaid plan" })).toHaveValue("plan-Welcome");
  });

  it("shows the monthly fee of the chosen plan before Save", async () => {
    renderControl();

    await openEditor();
    expect(screen.getByText("$65.00")).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Postpaid plan" }), "plan-Plus");

    expect(screen.getByText("$80.00")).toBeInTheDocument();
  });

  it("sends the chosen plan, then leaves editing and refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    renderControl();

    await openEditor();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Postpaid plan" }), "plan-Plus");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/sim-cards/sim-1/postpaid-plan",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ postpaidPlanId: "plan-Plus" }) }),
    );

    respond(200, { id: "sim-1" });
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    expect(screen.queryByRole("combobox", { name: "Postpaid plan" })).not.toBeInTheDocument();
  });

  it.each(["PLAN_ARCHIVED", "PLAN_NOT_ON_CARRIER", "CARRIER_NOT_USABLE"])(
    "shows the pick-another message for %s and keeps the choice",
    async (code) => {
      stubFetch(400, { code, message: "refused" });
      renderControl();

      await openEditor();
      await userEvent.selectOptions(screen.getByRole("combobox", { name: "Postpaid plan" }), "plan-Plus");
      await userEvent.click(screen.getByRole("button", { name: "Save" }));

      expect(await screen.findByText("That plan can’t be chosen any more. Pick another.")).toBeInTheDocument();
      expect(screen.getByRole("combobox", { name: "Postpaid plan" })).toHaveValue("plan-Plus");
      expect(mockRouter.refresh).not.toHaveBeenCalled();
    },
  );

  it("shows the generic message for an unknown code and keeps the choice", async () => {
    stubFetch(500, { code: "SOMETHING_ELSE" });
    renderControl();

    await openEditor();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Postpaid plan" }), "plan-Plus");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText(/Couldn.t update\. Try again\./)).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Postpaid plan" })).toHaveValue("plan-Plus");
  });

  it("Cancel restores the old plan without a request", async () => {
    const fetchMock = stubFetch(200);
    renderControl();

    await openEditor();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Postpaid plan" }), "plan-Plus");
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    expect(screen.getByText("Welcome")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("works by keyboard: Tab reaches Edit plan, Enter opens it, Enter on the select saves", async () => {
    const fetchMock = stubFetch(200, { id: "sim-1" });
    renderControl();

    await userEvent.tab();
    expect(screen.getByRole("button", { name: "Edit plan" })).toHaveFocus();
    await userEvent.keyboard("{Enter}");
    expect(screen.getByRole("combobox", { name: "Postpaid plan" })).toHaveFocus();
    await userEvent.keyboard("{Enter}");

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
  });

  it("starts with no plan chosen for a SIM whose plan is archived, and will not save until one is picked", async () => {
    const fetchMock = stubFetch(200);
    render(
      <SimCardPlanControl
        contractId="contract-1"
        simCardId="sim-1"
        sim={{ postpaidPlanId: "plan-Legacy", postpaidPlanName: "Legacy", postpaidPlanArchived: true }}
        carrier={carrier}
        currency="USD"
        carriersHref="/manager/carriers"
      />,
    );

    expect(screen.getByText("Archived")).toBeInTheDocument();
    await openEditor();
    expect(screen.getByRole("combobox", { name: "Postpaid plan" })).toHaveValue("");
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
