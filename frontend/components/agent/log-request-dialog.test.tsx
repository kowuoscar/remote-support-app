import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import { LogRequestDialog } from "./log-request-dialog";

const testers = [{ id: "tester-1", username: "priya@aurora.example" }] as never;

async function openDialog() {
  render(<LogRequestDialog contractId="contract-1" testers={testers} />);
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
