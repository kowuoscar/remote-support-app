import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import { CreateAgentLoginDialog } from "./create-agent-login-dialog";

const PASSWORD = "k7Qm-x2Vd-9Rtw";

async function openDialog(onCreated = vi.fn()) {
  render(<CreateAgentLoginDialog agentId="agent-1" agentName="Camille" onCreated={onCreated} />);
  await userEvent.click(screen.getByRole("button", { name: "Create login" }));
  const dialog = screen.getByRole("dialog");
  await userEvent.type(within(dialog).getByLabelText("Email"), "camille@agents.example");
  return { dialog, onCreated };
}

async function submit(dialog: HTMLElement) {
  await userEvent.click(within(dialog).getByRole("button", { name: "Create login" }));
}

const created = { id: "agent-1", loginUsername: "camille@agents.example", password: PASSWORD };

describe("CreateAgentLoginDialog", () => {
  it("has no password field and shows the generated-password hint", async () => {
    const { dialog } = await openDialog();

    expect(within(dialog).queryByLabelText(/password/i)).not.toBeInTheDocument();
    expect(
      within(dialog).getByText("A password is generated when you create the login — you'll see it once."),
    ).toBeInTheDocument();
  });

  it("shows the reveal on 201 and calls onCreated only once it is closed", async () => {
    const fetchMock = stubFetch(201, created);
    const { dialog, onCreated } = await openDialog();

    await submit(dialog);

    await waitFor(() => expect(within(dialog).getByLabelText("Generated password")).toHaveValue(PASSWORD));
    const sent = JSON.parse((fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].body as string);
    expect(sent).toEqual({ username: "camille@agents.example" });
    expect(onCreated).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole("button", { name: "Done" }));

    expect(onCreated).toHaveBeenCalledExactlyOnceWith("camille@agents.example");
    expect(screen.queryByDisplayValue(PASSWORD)).not.toBeInTheDocument();
  });

  it("calls onCreated when the reveal is closed with Escape", async () => {
    stubFetch(201, created);
    const { dialog, onCreated } = await openDialog();
    await submit(dialog);
    await within(dialog).findByLabelText("Generated password");

    dialog.dispatchEvent(new Event("cancel", { cancelable: true }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledExactlyOnceWith("camille@agents.example"));
  });

  it("does not call onCreated when the form is cancelled", async () => {
    const { dialog, onCreated } = await openDialog();

    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));

    expect(onCreated).not.toHaveBeenCalled();
  });

  it("shows the taken-email wording on a USERNAME_TAKEN 409", async () => {
    stubFetch(409, { code: "USERNAME_TAKEN" });
    const { dialog, onCreated } = await openDialog();

    await submit(dialog);

    expect(await screen.findByRole("alert")).toHaveTextContent("That email is already in use.");
    expect(onCreated).not.toHaveBeenCalled();
  });

  it("shows a generic message on any other failure", async () => {
    stubFetch(500);
    const { dialog } = await openDialog();

    await submit(dialog);

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't create the login. Try again.");
  });

  it("is named by its heading on the form step and on the reveal step", async () => {
    stubFetch(201, created);
    const { dialog } = await openDialog();
    expect(screen.getByRole("dialog", { name: "Create a login for Camille" })).toBe(dialog);

    await submit(dialog);

    await within(dialog).findByLabelText("Generated password");
    expect(screen.getByRole("dialog", { name: "Login created" })).toBe(dialog);
  });
});
