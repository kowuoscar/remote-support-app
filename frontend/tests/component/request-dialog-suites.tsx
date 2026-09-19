import { within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import { submitAndGetRequestBody } from "@/tests/component/submit-and-capture";
import {
  REBOOT_TOPUP_CARRIERS,
  REBOOT_TOPUP_SIM_CARDS,
  REBOOT_TOPUP_SMARTPHONES,
} from "@/components/requests/details/test-fixtures";

/**
 * The two Request dialogs — the Tester's SubmitRequestDialog and the Agent's LogRequestDialog —
 * are separate components that must behave identically where they share a feature: the generic
 * description field every type without its own details component falls back to, and the
 * Reboot/Topup details sections. These suites are registered by both test files rather than
 * written out twice, so a behaviour that drifts in one dialog fails the other's suite too.
 *
 * Each dialog passes its own way in: how it opens (their props differ — SubmitRequestDialog is
 * Contract-scoped through maps, LogRequestDialog through flat props), the label on its submit
 * button, and the fields its own body always carries (LogRequestDialog names a Tester).
 */
export type RequestDialogUnderTest = {
  /** Renders the dialog with these extra props and returns it, already open. */
  openDialog: (props?: Record<string, unknown>) => Promise<HTMLElement>;
  submitLabel: string;
  /** Fields this dialog always sends, merged into every body assertion (e.g. `testerId`). */
  alwaysSends?: Record<string, unknown>;
  /** The props this dialog takes to offer the given Smartphones / SIM Cards + Carriers. */
  rebootProps: (smartphones: typeof REBOOT_TOPUP_SMARTPHONES) => Record<string, unknown>;
  topupProps: (
    simCards: typeof REBOOT_TOPUP_SIM_CARDS,
    carriers: typeof REBOOT_TOPUP_CARRIERS,
  ) => Record<string, unknown>;
};

/**
 * Reboot and Topup have their own details component (reboot-and-topup-details ticket), each with
 * its own description field; every other type falls back to the dialog's generic one, which Other
 * makes required (other-replaces-repair ticket).
 */
export function describeGenericDescriptionField(name: string, dialog: RequestDialogUnderTest) {
  const { openDialog, submitLabel, alwaysSends = {} } = dialog;

  describe(`${name}'s description field`, () => {
    it("is optional for every type with no details component of its own", async () => {
      const opened = await openDialog();

      for (const label of ["SIM Swap", "Provision Smartphone", "Provision SIM"]) {
        await userEvent.selectOptions(within(opened).getByLabelText("Request type"), label);
        expect(within(opened).getByLabelText("Description (optional)")).not.toBeRequired();
      }
    });

    it("is required once Other is picked", async () => {
      const opened = await openDialog();

      await userEvent.selectOptions(within(opened).getByLabelText("Request type"), "Other");
      expect(within(opened).getByLabelText("Description")).toBeRequired();
    });

    it("sends the description that was typed", async () => {
      const fetchMock = stubFetch(201, { id: "request-1" });
      const opened = await openDialog();

      await userEvent.selectOptions(within(opened).getByLabelText("Request type"), "Other");
      await userEvent.type(within(opened).getByLabelText("Description"), "Cracked screen");
      const body = await submitAndGetRequestBody(opened, fetchMock, submitLabel);

      expect(body).toMatchObject({ ...alwaysSends, type: "OTHER", description: "Cracked screen" });
    });
  });
}

export function describeRebootAndTopupDetails(name: string, dialog: RequestDialogUnderTest) {
  const { openDialog, submitLabel, alwaysSends = {}, rebootProps, topupProps } = dialog;

  describe(`${name}'s Reboot and Topup details`, () => {
    it("sends the chosen Smartphone when submitting a Reboot", async () => {
      const fetchMock = stubFetch(201, { id: "request-1" });
      const opened = await openDialog(rebootProps(REBOOT_TOPUP_SMARTPHONES));

      await userEvent.selectOptions(within(opened).getByLabelText("Request type"), "Reboot");
      await userEvent.selectOptions(
        within(opened).getByLabelText("Smartphone to reboot"),
        "Pixel 9 — SN-1",
      );
      const body = await submitAndGetRequestBody(opened, fetchMock, submitLabel);

      expect(body).toMatchObject({ ...alwaysSends, type: "REBOOT", targetSmartphoneId: "phone-1" });
    });

    it("sends the chosen SIM Card and Topup Option when submitting a Topup", async () => {
      const fetchMock = stubFetch(201, { id: "request-1" });
      const opened = await openDialog(topupProps(REBOOT_TOPUP_SIM_CARDS, REBOOT_TOPUP_CARRIERS));

      await userEvent.selectOptions(within(opened).getByLabelText("Request type"), "Topup");
      await userEvent.selectOptions(
        within(opened).getByLabelText("SIM Card to top up"),
        within(opened).getByRole("option", { name: /\+1-555-0100/ }),
      );
      await userEvent.selectOptions(within(opened).getByLabelText("Topup Option"), "Refill 25");
      const body = await submitAndGetRequestBody(opened, fetchMock, submitLabel);

      expect(body).toMatchObject({
        ...alwaysSends,
        type: "TOPUP",
        targetSimCardId: "sim-1",
        topupOptionId: "option-1",
      });
    });
  });
}
