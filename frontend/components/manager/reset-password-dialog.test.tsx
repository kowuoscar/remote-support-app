import { useRef } from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { ResetPasswordDialog, type ResetPasswordDialogHandle } from "./reset-password-dialog";

const PASSWORD = "k7Qm-x2Vd-9Rtw";

const target = {
  name: "Camille Duforet",
  email: "camille@agents.example",
  endpoint: "/api/agents/agent-1/login/password",
  listLink: { href: "/manager/agents", label: "Back to the Agents list" },
};

function Host({ onReset }: Readonly<{ onReset: (email: string) => void }>) {
  const ref = useRef<ResetPasswordDialogHandle>(null);
  return (
    <>
      <button type="button" onClick={() => ref.current?.open(target)}>
        Open reset
      </button>
      <ResetPasswordDialog ref={ref} onReset={onReset} />
    </>
  );
}

async function openDialog(onReset = vi.fn()) {
  render(<Host onReset={onReset} />);
  const trigger = screen.getByRole("button", { name: "Open reset" });
  await userEvent.click(trigger);
  return { trigger, dialog: screen.getByRole("dialog"), onReset };
}

async function confirm(dialog: HTMLElement) {
  await userEvent.click(within(dialog).getByRole("button", { name: "Reset password" }));
}

describe("ResetPasswordDialog", () => {
  it("asks for confirmation, naming the person and saying the current password stops working", async () => {
    const { dialog } = await openDialog();

    expect(within(dialog).getByRole("heading", { name: "Reset password for Camille Duforet" })).toBeInTheDocument();
    expect(
      within(dialog).getByText(
        "A new password will be generated for camille@agents.example. Their current password stops working as soon as you confirm.",
      ),
    ).toBeInTheDocument();
  });

  it("sends nothing and reports no reset when cancelled", async () => {
    const fetchMock = stubFetch(200, { password: PASSWORD });
    const { dialog, onReset } = await openDialog();

    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));

    expect(fetchMock).not.toHaveBeenCalled();
    expect(onReset).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: "Reset password" })).not.toBeInTheDocument();
  });

  it("posts to the target's endpoint and shows the reveal with the new password", async () => {
    const fetchMock = stubFetch(200, { password: PASSWORD });
    const { dialog } = await openDialog();

    await confirm(dialog);

    expect(await within(dialog).findByLabelText("Generated password")).toHaveValue(PASSWORD);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/agents/agent-1/login/password");
    expect(init.method).toBe("POST");
    expect(
      within(dialog).getByText(
        "camille@agents.example can now sign in with this new password. Their old one no longer works.",
      ),
    ).toBeInTheDocument();
  });

  it("disables Cancel while the reset is in flight", async () => {
    const { respond } = stubPendingFetch();
    const { dialog } = await openDialog();

    await confirm(dialog);

    expect(within(dialog).getByRole("button", { name: "Cancel" })).toBeDisabled();
    respond(200, { password: PASSWORD });
    await within(dialog).findByLabelText("Generated password");
  });

  it("closes on Done: focus returns to the trigger, onReset names the email, the password is gone", async () => {
    stubFetch(200, { password: PASSWORD });
    const { trigger, dialog, onReset } = await openDialog();
    await confirm(dialog);
    await within(dialog).findByLabelText("Generated password");

    await userEvent.click(within(dialog).getByRole("button", { name: "Done" }));

    await waitFor(() => expect(trigger).toHaveFocus());
    expect(onReset).toHaveBeenCalledExactlyOnceWith("camille@agents.example");
    expect(document.body.innerHTML).not.toContain(PASSWORD);
    expect(screen.queryByDisplayValue(PASSWORD)).not.toBeInTheDocument();
  });

  it("shows the confirm step again when reopened after a reset", async () => {
    stubFetch(200, { password: PASSWORD });
    const { trigger, dialog } = await openDialog();
    await confirm(dialog);
    await userEvent.click(await within(dialog).findByRole("button", { name: "Done" }));

    await userEvent.click(trigger);

    const reopened = screen.getByRole("dialog");
    expect(within(reopened).getByRole("button", { name: "Reset password" })).toBeInTheDocument();
    expect(screen.queryByDisplayValue(PASSWORD)).not.toBeInTheDocument();
  });

  it("says the page is stale on 409 AGENT_HAS_NO_LOGIN", async () => {
    stubFetch(409, { code: "AGENT_HAS_NO_LOGIN" });
    const { dialog } = await openDialog();

    await confirm(dialog);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Camille Duforet has no login to reset. This page is out of date — refresh it.",
    );
  });

  it("says the person no longer exists, with a link back to the list, on 404", async () => {
    stubFetch(404);
    const { dialog } = await openDialog();

    await confirm(dialog);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Camille Duforet no longer exists.");
    expect(within(alert).getByRole("link", { name: "Back to the Agents list" })).toHaveAttribute(
      "href",
      "/manager/agents",
    );
  });

  it.each([
    ["a 500", () => stubFetch(500)],
    ["a lost connection", () => stubFetch(200).mockRejectedValue(new TypeError("network"))],
  ])("shows the generic message that the old password may have stopped working on %s", async (_name, stub) => {
    stub();
    const { dialog } = await openDialog();

    await confirm(dialog);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Couldn't reset the password. Their old password may already have stopped working — try again to get a new one.",
    );
    expect(within(dialog).getByRole("button", { name: "Reset password" })).toBeEnabled();
  });
});
