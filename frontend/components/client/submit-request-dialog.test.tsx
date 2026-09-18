import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it } from "vitest";
import type { ComponentProps } from "react";
import { stubFetch } from "@/tests/component/fetch";
import { SubmitRequestDialog } from "./submit-request-dialog";

const contracts = [{ id: "contract-1", label: "Aurora Retail Group · USD", currency: "USD" }];

async function openDialog(props: Partial<ComponentProps<typeof SubmitRequestDialog>> = {}) {
  render(<SubmitRequestDialog contracts={contracts} {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "Submit Request" }));
  return screen.getByRole("dialog");
}

describe("SubmitRequestDialog's description field", () => {
  beforeAll(() => {
    // jsdom has no modal dialog; opening it is all these tests need.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  // Reboot and Topup now have their own details component (reboot-and-topup-details ticket), each
  // with its own description field — see reboot-and-topup-details-section.test.tsx below and the
  // per-type component tests. The other types still fall back to this dialog's own generic one.
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

  it("sends the description that was typed", async () => {
    const fetchMock = stubFetch(201, { id: "request-1" });
    const dialog = await openDialog();

    await userEvent.selectOptions(within(dialog).getByLabelText("Request type"), "Other");
    await userEvent.type(within(dialog).getByLabelText("Description"), "Screen protector is peeling");
    await userEvent.click(within(dialog).getByRole("button", { name: "Submit Request" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(String(init.body))).toMatchObject({
      type: "OTHER",
      description: "Screen protector is peeling",
    });
  });

  it("omits the description when none was given for an optional type", async () => {
    const fetchMock = stubFetch(201, { id: "request-1" });
    const dialog = await openDialog();

    await userEvent.click(within(dialog).getByRole("button", { name: "Submit Request" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(String(init.body))).not.toHaveProperty("description");
  });
});

// reboot-and-topup-details ticket: the Reboot/Topup details sections are scoped to whichever
// Contract this dialog's own picker names — a fixture of one Contract's Smartphones/SIM Cards is
// enough since this suite never switches Contract.
describe("SubmitRequestDialog's Reboot and Topup details", () => {
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

  it("sends the chosen Smartphone when submitting a Reboot", async () => {
    const fetchMock = stubFetch(201, { id: "request-1" });
    const dialog = await openDialog({
      smartphonesByContract: { "contract-1": smartphones },
    });

    await userEvent.selectOptions(within(dialog).getByLabelText("Request type"), "Reboot");
    await userEvent.selectOptions(within(dialog).getByLabelText("Smartphone to reboot"), "Pixel 9 — SN-1");
    await userEvent.click(within(dialog).getByRole("button", { name: "Submit Request" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(String(init.body))).toMatchObject({ type: "REBOOT", targetSmartphoneId: "phone-1" });
  });

  it("sends the chosen SIM Card and Topup Option when submitting a Topup", async () => {
    const fetchMock = stubFetch(201, { id: "request-1" });
    const dialog = await openDialog({
      simCardsByContract: { "contract-1": simCards },
      carriersByContract: { "contract-1": carriers },
    });

    await userEvent.selectOptions(within(dialog).getByLabelText("Request type"), "Topup");
    await userEvent.selectOptions(
      within(dialog).getByLabelText("SIM Card to top up"),
      within(dialog).getByRole("option", { name: /\+1-555-0100/ }),
    );
    await userEvent.selectOptions(within(dialog).getByLabelText("Topup Option"), "Refill 25");
    await userEvent.click(within(dialog).getByRole("button", { name: "Submit Request" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(String(init.body))).toMatchObject({
      type: "TOPUP",
      targetSimCardId: "sim-1",
      topupOptionId: "option-1",
    });
  });
});
