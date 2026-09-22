import { useRef } from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { ChangePasswordDialog, type ChangePasswordDialogHandle } from "./change-password-dialog";

/**
 * `ChangePasswordDialog` has no trigger button of its own — like `ArchiveCarrierDialog`, it is
 * opened imperatively by whatever renders it (`ViewerActions`, from `ViewerMenu`'s "Change password"
 * item). This harness stands in for that caller, the same shape `carriers-view.tsx` uses for
 * `ArchiveCarrierDialog`.
 */
function Harness() {
  const ref = useRef<ChangePasswordDialogHandle>(null);
  return (
    <>
      <button type="button" onClick={() => ref.current?.open()}>
        Open
      </button>
      <ChangePasswordDialog ref={ref} />
    </>
  );
}

async function openDialog() {
  render(<Harness />);
  await userEvent.click(screen.getByRole("button", { name: "Open" }));
  return screen.getByRole("dialog");
}

async function fillAndSubmit(
  dialog: HTMLElement,
  values: { current: string; next: string; confirm: string },
) {
  await userEvent.type(within(dialog).getByLabelText("Current password"), values.current);
  await userEvent.type(within(dialog).getByLabelText("New password"), values.next);
  await userEvent.type(within(dialog).getByLabelText("Confirm new password"), values.confirm);
  await userEvent.click(within(dialog).getByRole("button", { name: "Change password" }));
}

describe("ChangePasswordDialog", () => {
  beforeEach(() => {
    mockRouter.push.mockReset();
    mockRouter.refresh.mockReset();
  });

  it("blocks submission client-side when new and confirm differ, and sends no request", async () => {
    const fetchMock = stubFetch(204);
    const dialog = await openDialog();

    await fillAndSubmit(dialog, { current: "OldPassw0rd!", next: "NewPassw0rd!", confirm: "Mismatch1!" });

    expect(await screen.findByRole("alert")).toHaveTextContent("New password and confirmation don't match.");
    expect(within(dialog).getByLabelText("Confirm new password")).toHaveAttribute("aria-invalid", "true");
    expect(fetchMock).not.toHaveBeenCalled();
    // The typed values are left exactly as they were, not cleared.
    expect(within(dialog).getByLabelText("Current password")).toHaveValue("OldPassw0rd!");
    expect(within(dialog).getByLabelText("New password")).toHaveValue("NewPassw0rd!");
  });

  it("renders a wrong-current-password refusal on the current-password field", async () => {
    stubFetch(400, { code: "WRONG_CURRENT_PASSWORD", message: "irrelevant backend wording" });
    const dialog = await openDialog();

    await fillAndSubmit(dialog, { current: "WrongPassw0rd!", next: "NewPassw0rd!", confirm: "NewPassw0rd!" });

    expect(await screen.findByRole("alert")).toHaveTextContent("That's not your current password.");
    expect(within(dialog).getByLabelText("Current password")).toHaveAttribute("aria-invalid", "true");
    expect(within(dialog).getByLabelText("New password")).not.toHaveAttribute("aria-invalid", "true");
    expect(mockRouter.push).not.toHaveBeenCalled();
  });

  it("renders a new-identical-to-current refusal on the new-password field", async () => {
    stubFetch(400, { code: "PASSWORD_UNCHANGED", message: "irrelevant backend wording" });
    const dialog = await openDialog();

    await fillAndSubmit(dialog, { current: "SamePassw0rd!", next: "SamePassw0rd!", confirm: "SamePassw0rd!" });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Choose a new password that's different from your current one.",
    );
    expect(within(dialog).getByLabelText("New password")).toHaveAttribute("aria-invalid", "true");
    expect(within(dialog).getByLabelText("Current password")).not.toHaveAttribute("aria-invalid", "true");
    expect(mockRouter.push).not.toHaveBeenCalled();
  });

  it("renders a too-short refusal on the new-password field", async () => {
    stubFetch(400, { code: "PASSWORD_TOO_SHORT", message: "irrelevant backend wording" });
    const dialog = await openDialog();

    await fillAndSubmit(dialog, { current: "OldPassw0rd!", next: "short1", confirm: "short1" });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Your new password needs to be at least 8 characters.",
    );
    expect(within(dialog).getByLabelText("New password")).toHaveAttribute("aria-invalid", "true");
    expect(mockRouter.push).not.toHaveBeenCalled();
  });

  it("on a 204 success, drops the session and lands on sign-in with a confirmation", async () => {
    const fetchMock = stubFetch(204);
    const dialog = await openDialog();

    await fillAndSubmit(dialog, { current: "OldPassw0rd!", next: "NewPassw0rd!", confirm: "NewPassw0rd!" });

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/session", { method: "DELETE" }));
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/me/password",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ currentPassword: "OldPassw0rd!", newPassword: "NewPassw0rd!" }),
      }),
    );
    await waitFor(() => expect(mockRouter.push).toHaveBeenCalledWith("/login?passwordChanged=1"));
    expect(mockRouter.refresh).toHaveBeenCalled();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
