import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { CreateTesterDialog } from "./create-tester-dialog";

async function openDialog() {
  render(<CreateTesterDialog clientId="client-1" />);
  await userEvent.click(screen.getByRole("button", { name: "Add tester" }));
  const dialog = screen.getByRole("dialog");
  await userEvent.type(within(dialog).getByLabelText("Email"), "tom.reyes@client.example");
  return dialog;
}

async function submit(dialog: HTMLElement) {
  await userEvent.click(within(dialog).getByRole("button", { name: "Add tester" }));
}

describe("CreateTesterDialog", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("shows the taken-email wording and marks the email field on a USERNAME_TAKEN 409", async () => {
    stubFetch(409, { code: "USERNAME_TAKEN", message: "irrelevant backend wording" });
    const dialog = await openDialog();

    await submit(dialog);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "That email is already in use. Choose another one and try again.",
    );
    expect(within(dialog).getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("shows its own distinct message and leaves the email field alone on a PRIMARY_CONTACT_EXISTS 409", async () => {
    stubFetch(409, { code: "PRIMARY_CONTACT_EXISTS", message: "irrelevant backend wording" });
    const dialog = await openDialog();

    await submit(dialog);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("This client already has a primary contact");
    expect(alert).not.toHaveTextContent("That email is already in use");
    expect(within(dialog).getByLabelText("Email")).not.toHaveAttribute("aria-invalid", "true");
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("has no password field and shows the generated-password hint", async () => {
    const dialog = await openDialog();

    expect(within(dialog).queryByLabelText(/password/i)).not.toBeInTheDocument();
    expect(
      within(dialog).getByText("A password is generated when you create the login — you'll see it once."),
    ).toBeInTheDocument();
  });

  it("sends no password, shows the reveal and refreshes on success", async () => {
    const fetchMock = stubFetch(201, {
      id: "tester-1",
      username: "tom.reyes@client.example",
      password: "k7Qm-x2Vd-9Rtw",
    });
    const dialog = await openDialog();

    await submit(dialog);

    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    expect(JSON.parse((fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].body as string)).toEqual({
      username: "tom.reyes@client.example",
      isPrimaryContact: false,
    });
    expect(within(dialog).getByLabelText("Generated password")).toHaveValue("k7Qm-x2Vd-9Rtw");
    expect(within(dialog).getByText(/tom\.reyes@client\.example can now sign in/)).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "Done" }));
    expect(screen.queryByDisplayValue("k7Qm-x2Vd-9Rtw")).not.toBeInTheDocument();
  });

  it("shows a generic message on a non-409 failure", async () => {
    stubFetch(500);
    const dialog = await openDialog();

    await submit(dialog);

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't create the tester. Try again.");
  });

  it("is named by its heading on the form step and on the reveal step", async () => {
    stubFetch(201, { id: "tester-1", username: "tom.reyes@client.example", password: "k7Qm-x2Vd-9Rtw" });
    const dialog = await openDialog();
    expect(screen.getByRole("dialog", { name: "Add a tester" })).toBe(dialog);

    await submit(dialog);

    await within(dialog).findByLabelText("Generated password");
    expect(screen.getByRole("dialog", { name: "Login created" })).toBe(dialog);
  });
});
