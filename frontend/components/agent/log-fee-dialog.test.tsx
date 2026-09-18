import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import type { CatalogCarrierItem } from "@/lib/api/types";
import { LogFeeDialog } from "./log-fee-dialog";

const testers = [{ id: "tester-1", username: "priya@aurora.example" }] as never;

function offer(id: string, carrierId: string, name: string, price: number, archivedAt: string | null = null) {
  return { id, carrierId, name, price, archivedAt };
}

const carriers: CatalogCarrierItem[] = [
  {
    id: "att",
    country: "UNITED_STATES",
    name: "AT&T",
    archivedAt: null,
    topupOptions: [
      offer("refill-25", "att", "Prepaid Refill 25", 25),
      offer("refill-5", "att", "Refill 5", 5, "2024-06-01T00:00:00Z"),
    ],
    postpaidPlans: [],
  },
  {
    id: "sprint",
    country: "UNITED_STATES",
    name: "Sprint",
    archivedAt: "2024-06-01T00:00:00Z",
    topupOptions: [offer("sprint-10", "sprint", "Sprint Refill 10", 10)],
    postpaidPlans: [],
  },
];

async function openDialog() {
  render(
    <LogFeeDialog
      contractId="contract-1"
      currency="USD"
      testers={testers}
      carriers={carriers}
      carriersHref="/agent/carriers"
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "Log a fee" }));
  return screen.getByRole("dialog");
}

describe("LogFeeDialog's Topup Option picker", () => {
  beforeAll(() => {
    // jsdom has no modal dialog; opening it is all these tests need.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it("lists only the active Options of active Carriers, labelled by Carrier and Option", async () => {
    const dialog = await openDialog();
    const picker = within(dialog).getByLabelText("Topup option (optional)");

    expect(
      within(picker).getByRole("option", { name: "AT&T — Prepaid Refill 25 · $25.00" }),
    ).toBeInTheDocument();
    expect(within(picker).queryByRole("option", { name: /Refill 5\b/ })).not.toBeInTheDocument();
    expect(within(picker).queryByRole("option", { name: /Sprint/ })).not.toBeInTheDocument();
  });

  it("pre-fills the amount from the picked Option, keeps it editable, and sends both", async () => {
    const fetchMock = stubFetch(201, {});
    const dialog = await openDialog();

    const picker = within(dialog).getByLabelText("Topup option (optional)");
    await userEvent.selectOptions(
      picker,
      within(picker).getByRole("option", { name: "AT&T — Prepaid Refill 25 · $25.00" }),
    );
    const amount = within(dialog).getByLabelText("Amount (USD)");
    expect(amount).toHaveValue(25);

    await userEvent.clear(amount);
    await userEvent.type(amount, "27.50");
    await userEvent.click(within(dialog).getByRole("button", { name: "Log fee" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(String(init.body))).toMatchObject({
      feeType: "TOPUP",
      amount: 27.5,
      topupOptionId: "refill-25",
    });
  });

  it("logs a Topup Fee with no Option", async () => {
    const fetchMock = stubFetch(201, {});
    const dialog = await openDialog();

    await userEvent.type(within(dialog).getByLabelText("Amount (USD)"), "12");
    await userEvent.click(within(dialog).getByRole("button", { name: "Log fee" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(String(init.body))).not.toHaveProperty("topupOptionId");
  });

  it("is hidden for Fee types other than Topup", async () => {
    const dialog = await openDialog();

    for (const label of ["Provision Smartphone", "Provision SIM", "Repair"]) {
      await userEvent.selectOptions(within(dialog).getByLabelText("Fee type"), label);
      expect(within(dialog).queryByLabelText("Topup option (optional)")).not.toBeInTheDocument();
    }
  });
});
