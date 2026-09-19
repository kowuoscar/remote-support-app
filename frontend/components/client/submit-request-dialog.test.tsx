import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { ComponentProps } from "react";
import { stubFetch } from "@/tests/component/fetch";
import { submitAndGetRequestBody } from "@/tests/component/submit-and-capture";
import {
  describeGenericDescriptionField,
  describeRebootAndTopupDetails,
} from "@/tests/component/request-dialog-suites";
import { SubmitRequestDialog } from "./submit-request-dialog";

const contracts = [{ id: "contract-1", label: "Aurora Retail Group · USD", currency: "USD" }];

async function openDialog(props: Partial<ComponentProps<typeof SubmitRequestDialog>> = {}) {
  render(<SubmitRequestDialog contracts={contracts} {...props} />);
  await userEvent.click(screen.getByRole("button", { name: "Submit Request" }));
  return screen.getByRole("dialog");
}

// What this dialog shares with the Agent's LogRequestDialog lives in one place, registered by
// both (request-dialog-suites.tsx). Its Reboot/Topup details are scoped to whichever Contract its
// own picker names, so the fixtures arrive as per-Contract maps; one Contract is enough, since
// this suite never switches Contract.
const dialogUnderTest = {
  openDialog: (props: Record<string, unknown> = {}) => openDialog(props),
  submitLabel: "Submit Request",
  rebootProps: (smartphones: unknown) => ({ smartphonesByContract: { "contract-1": smartphones } }),
  topupProps: (simCards: unknown, carriers: unknown) => ({
    simCardsByContract: { "contract-1": simCards },
    carriersByContract: { "contract-1": carriers },
  }),
} as Parameters<typeof describeGenericDescriptionField>[1];

describeGenericDescriptionField("SubmitRequestDialog", dialogUnderTest);
describeRebootAndTopupDetails("SubmitRequestDialog", dialogUnderTest);

describe("SubmitRequestDialog's own description handling", () => {
  it("omits the description when none was given for an optional type", async () => {
    const fetchMock = stubFetch(201, { id: "request-1" });
    const dialog = await openDialog();

    const body = await submitAndGetRequestBody(dialog, fetchMock, "Submit Request");

    expect(body).not.toHaveProperty("description");
  });
});
