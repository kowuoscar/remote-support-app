import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { CreateAgentDialog } from "./create-agent-dialog";

const PASSWORD = "k7Qm-x2Vd-9Rtw";

async function openDialog() {
  render(<CreateAgentDialog />);
  await userEvent.click(screen.getByRole("button", { name: "Add agent" }));
  const dialog = screen.getByRole("dialog");
  await userEvent.type(within(dialog).getByLabelText("Agent name"), "Camille Duforet");
  await userEvent.type(within(dialog).getByLabelText("Standing monthly salary"), "2400");
  await userEvent.type(within(dialog).getByLabelText("Email"), "camille@agents.example");
  return dialog;
}

async function submit(dialog: HTMLElement) {
  await userEvent.click(within(dialog).getByRole("button", { name: "Add agent" }));
}

describe("CreateAgentDialog", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("has no password field and shows the generated-password hint", async () => {
    const dialog = await openDialog();

    expect(within(dialog).queryByLabelText(/password/i)).not.toBeInTheDocument();
    expect(
      within(dialog).getByText("A password is generated when you create the login — you'll see it once."),
    ).toBeInTheDocument();
  });

  it("sends no password, shows the reveal and refreshes behind it on 201", async () => {
    const fetchMock = stubFetch(201, { id: "a-1", loginUsername: "camille@agents.example", password: PASSWORD });
    const dialog = await openDialog();

    await submit(dialog);

    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
    const sent = JSON.parse((fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].body as string);
    expect(sent).not.toHaveProperty("password");
    expect(within(dialog).getByLabelText("Generated password")).toHaveValue(PASSWORD);
    expect(within(dialog).getByText(/camille@agents\.example can now sign in/)).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "Done" }));
    expect(screen.queryByDisplayValue(PASSWORD)).not.toBeInTheDocument();
  });

  it("shows the taken-email wording on a USERNAME_TAKEN 409", async () => {
    stubFetch(409, { code: "USERNAME_TAKEN" });
    const dialog = await openDialog();

    await submit(dialog);

    expect(await screen.findByRole("alert")).toHaveTextContent("That email is already in use.");
    expect(within(dialog).getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
  });

  it("shows a generic message on any other failure", async () => {
    stubFetch(500);
    const dialog = await openDialog();

    await submit(dialog);

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't create the agent. Try again.");
  });

  it("is named by its heading on the form step and on the reveal step", async () => {
    stubFetch(201, { id: "a-1", loginUsername: "camille@agents.example", password: PASSWORD });
    const dialog = await openDialog();
    expect(screen.getByRole("dialog", { name: "Add an agent" })).toBe(dialog);

    await submit(dialog);

    await within(dialog).findByLabelText("Generated password");
    expect(screen.getByRole("dialog", { name: "Login created" })).toBe(dialog);
  });
});
