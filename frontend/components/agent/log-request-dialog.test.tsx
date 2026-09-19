import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { ComponentProps } from "react";
import { stubFetch } from "@/tests/component/fetch";
import { submitAndGetRequestBody } from "@/tests/component/submit-and-capture";
import {
  describeGenericDescriptionField,
  describeRebootAndTopupDetails,
} from "@/tests/component/request-dialog-suites";
import { LogRequestDialog } from "./log-request-dialog";

const testers = [{ id: "tester-1", username: "priya@aurora.example" }] as never;

async function openDialog(props: Partial<ComponentProps<typeof LogRequestDialog>> = {}) {
  render(<LogRequestDialog contractId="contract-1" testers={testers} {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "Log a request" }));
  return screen.getByRole("dialog");
}

// What this dialog shares with the Tester's SubmitRequestDialog lives in one place, registered by
// both (request-dialog-suites.tsx). This dialog is already scoped to one fixed Contract, so its
// Smartphones/SIM Cards/Carriers are flat props, and every body it sends names the Tester.
const dialogUnderTest = {
  openDialog: (props: Record<string, unknown> = {}) => openDialog(props),
  submitLabel: "Log request",
  alwaysSends: { testerId: "tester-1" },
  rebootProps: (smartphones: unknown) => ({ smartphones }),
  topupProps: (simCards: unknown, carriers: unknown) => ({ simCards, carriers }),
} as Parameters<typeof describeGenericDescriptionField>[1];

describeGenericDescriptionField("LogRequestDialog", dialogUnderTest);
describeRebootAndTopupDetails("LogRequestDialog", dialogUnderTest);

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
    const body = await submitAndGetRequestBody(dialog, fetchMock, "Log request");

    expect(body.type).toBe("PROVISION_SMARTPHONE");
    expect(body.startingStatus).toBeUndefined();
  });
});
