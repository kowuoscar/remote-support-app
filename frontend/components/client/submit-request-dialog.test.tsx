import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import { SubmitRequestDialog } from "./submit-request-dialog";

const contracts = [{ id: "contract-1", label: "Aurora Retail Group · USD", currency: "USD" }];

async function openDialog() {
  render(<SubmitRequestDialog contracts={contracts} />);
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

  it("is optional for every type except Other", async () => {
    const dialog = await openDialog();

    for (const label of ["Reboot", "Topup", "SIM Swap", "Provision Smartphone", "Provision SIM"]) {
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
