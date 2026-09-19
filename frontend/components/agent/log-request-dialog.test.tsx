import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it } from "vitest";
import type { ComponentProps } from "react";
import { stubFetch } from "@/tests/component/fetch";
import { LogRequestDialog } from "./log-request-dialog";

const testers = [{ id: "tester-1", username: "priya@aurora.example" }] as never;

async function openDialog(props: Partial<ComponentProps<typeof LogRequestDialog>> = {}) {
  render(<LogRequestDialog contractId="contract-1" testers={testers} {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "Log a request" }));
  return screen.getByRole("dialog");
}

describe("LogRequestDialog's description field", () => {
  beforeAll(() => {
    // jsdom has no modal dialog; opening it is all these tests need.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  // Reboot and Topup now have their own details component (reboot-and-topup-details ticket) —
  // see the "Reboot and Topup details" suite below and the per-type component tests.
  it("is optional for every type with no details component of its own", async () => {
    const dialog = await openDialog();

    for (const label of ["SIM Swap", "Provision Smartphone", "Provision SIM"]) {
      await userEvent.selectOptions(within(dialog).getByLabelText("Request type"), label);
      expect(within(dialog).getByLabelText("Description (optional)")).not.toBeRequired();
    }
  });

  it("is required once Other is picked", async () => {
    const dialog = await openDialog();

    await userEvent.selectOptions(within(dialog).getByLabelText("Request type"), "Other");
    expect(within(dialog).getByLabelText("Description")).toBeRequired();
  });

  it("sends the description that was typed, along with the type and tester", async () => {
    const fetchMock = stubFetch(201, {});
    const dialog = await openDialog();

    await userEvent.selectOptions(within(dialog).getByLabelText("Request type"), "Other");
    await userEvent.type(within(dialog).getByLabelText("Description"), "Cracked screen");
    await userEvent.click(within(dialog).getByRole("button", { name: "Log request" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(String(init.body))).toMatchObject({
      type: "OTHER",
      testerId: "tester-1",
      description: "Cracked screen",
    });
  });
});

// manager-approves-requests ticket: Provision Smartphone/SIM and Replace Smartphone/SIM always
// start Pending Approval now — an Agent can no longer choose the starting status for these types.
describe("LogRequestDialog's starting status, for the four approval-required types", () => {
  it("hides the starting-status choice and explains why, for an approval-required type", async () => {
    const dialog = await openDialog();

    await userEvent.selectOptions(within(dialog).getByLabelText("Request type"), "Provision Smartphone");

    expect(within(dialog).queryByText("Starting status")).not.toBeInTheDocument();
    expect(within(dialog).getByText(/always starts Pending Approval/)).toBeInTheDocument();
  });

  it("still offers the starting-status choice for a type that doesn't need approval", async () => {
    const dialog = await openDialog();

    await userEvent.selectOptions(within(dialog).getByLabelText("Request type"), "SIM Swap");

    expect(within(dialog).getByText("Starting status")).toBeInTheDocument();
  });

  it("sends no startingStatus at all for an approval-required type", async () => {
    const fetchMock = stubFetch(201, {});
    const dialog = await openDialog();

    await userEvent.selectOptions(within(dialog).getByLabelText("Request type"), "Provision Smartphone");
    await userEvent.type(within(dialog).getByLabelText(/Requested model/), "iPhone 15");
    await userEvent.click(within(dialog).getByRole("button", { name: "Log request" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    const body = JSON.parse(String(init.body));
    expect(body.type).toBe("PROVISION_SMARTPHONE");
    expect(body.startingStatus).toBeUndefined();
  });
});

// reboot-and-topup-details ticket: LogRequestDialog is already scoped to one fixed Contract, so
// its Smartphones/SIM Cards/Carriers are plain flat props (the caller — AgentRequestsView —
// already filters them to that Contract).
describe("LogRequestDialog's Reboot and Topup details", () => {
  const smartphones = [
    { id: "phone-1", contractId: "contract-1", model: "Pixel 9", serial: "SN-1", owner: "COMPANY" as const, status: "ACTIVE" as const },
  ];
  const simCards = [
    {
      id: "sim-1",
      contractId: "contract-1",
      number: "+1-555-0100",
      carrierId: "carrier-1",
      carrierName: "AT&T",
      flavor: "PREPAID" as const,
      monthlyFeeAmount: null,
      status: "ACTIVE" as const,
    },
  ];
  const carriers = [
    {
      id: "carrier-1",
      country: "UNITED_STATES" as const,
      name: "AT&T",
      archivedAt: null,
      topupOptions: [{ id: "option-1", carrierId: "carrier-1", name: "Refill 25", price: 25, archivedAt: null }],
      postpaidPlans: [],
    },
  ];

  it("sends the chosen Smartphone and Tester when logging a Reboot", async () => {
    const fetchMock = stubFetch(201, {});
    const dialog = await openDialog({ smartphones });

    await userEvent.selectOptions(within(dialog).getByLabelText("Request type"), "Reboot");
    await userEvent.selectOptions(within(dialog).getByLabelText("Smartphone to reboot"), "Pixel 9 — SN-1");
    await userEvent.click(within(dialog).getByRole("button", { name: "Log request" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(String(init.body))).toMatchObject({
      type: "REBOOT",
      testerId: "tester-1",
      targetSmartphoneId: "phone-1",
    });
  });

  it("sends the chosen SIM Card and Topup Option when logging a Topup", async () => {
    const fetchMock = stubFetch(201, {});
    const dialog = await openDialog({ simCards, carriers });

    await userEvent.selectOptions(within(dialog).getByLabelText("Request type"), "Topup");
    await userEvent.selectOptions(
      within(dialog).getByLabelText("SIM Card to top up"),
      within(dialog).getByRole("option", { name: /\+1-555-0100/ }),
    );
    await userEvent.selectOptions(within(dialog).getByLabelText("Topup Option"), "Refill 25");
    await userEvent.click(within(dialog).getByRole("button", { name: "Log request" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(String(init.body))).toMatchObject({
      type: "TOPUP",
      targetSimCardId: "sim-1",
      topupOptionId: "option-1",
    });
  });
});
