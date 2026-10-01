import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import type { TesterListItem } from "@/lib/api/types";
import { ManagerTestersView } from "./testers-view";

const testers: TesterListItem[] = [
  { id: "t-1", clientId: "client-1", username: "ada@client.example", isPrimaryContact: true },
  { id: "t-2", clientId: "client-1", username: "ben@client.example", isPrimaryContact: false },
  { id: "t-3", clientId: "client-1", username: "cleo@client.example", isPrimaryContact: false },
];

function renderView() {
  return render(<ManagerTestersView clientId="client-1" testers={testers} />);
}

describe("ManagerTestersView reset password", () => {
  it("offers one Reset password action per Tester, each named with that Tester's email", () => {
    renderView();

    for (const tester of testers) {
      expect(screen.getByRole("button", { name: `Reset password for ${tester.username}` })).toBeInTheDocument();
    }
    expect(screen.getAllByRole("columnheader", { name: "Actions" })).toHaveLength(1);
    // The email stays answered by exactly one cell (the e2e flows locate a Tester that way).
    expect(screen.getAllByRole("cell", { name: "ben@client.example" })).toHaveLength(1);
  });

  it("holds one reset dialog for many rows, with no form in it while closed", () => {
    renderView();

    const dialogs = Array.from(document.querySelectorAll("dialog"));
    expect(dialogs).toHaveLength(2); // Add tester + the one reset dialog, however many rows
    expect(document.querySelectorAll("dialog form")).toHaveLength(0);
  });

  it("opens for the chosen row, posts to that Tester's route and announces the reset without the password", async () => {
    const fetchMock = stubFetch(200, { password: "k7Qm-x2Vd-9Rtw" });
    renderView();
    const trigger = screen.getByRole("button", { name: "Reset password for ben@client.example" });

    await userEvent.click(trigger);
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("heading", { name: "Reset password for ben@client.example" })).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Reset password" }));
    expect(fetchMock).toHaveBeenCalledWith("/api/clients/client-1/testers/t-2/password", { method: "POST" });
    await userEvent.click(await within(dialog).findByRole("button", { name: "Done" }));

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Password reset for ben@client.example."));
    expect(screen.getByRole("status")).not.toHaveTextContent("k7Qm");
    expect(trigger).toHaveFocus();
  });

  it("says the Tester no longer exists on a 404", async () => {
    stubFetch(404);
    renderView();

    await userEvent.click(screen.getByRole("button", { name: "Reset password for cleo@client.example" }));
    const dialog = screen.getByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Reset password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("cleo@client.example no longer exists.");
  });
});
