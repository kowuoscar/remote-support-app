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

function renderView(list: TesterListItem[] = testers) {
  return render(<ManagerTestersView clientId="client-1" testers={list} />);
}

const withDeactivated: TesterListItem[] = [
  ...testers.slice(0, 2),
  { ...testers[2], deactivatedAt: "2026-10-02T09:14:00Z" },
];

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
    expect(dialogs).toHaveLength(4); // Add tester + the one reset, deactivate and reactivate dialogs, however many rows
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

  it("announces a second reset of the same Tester afresh: the region empties before the message returns", async () => {
    stubFetch(200, { password: "k7Qm-x2Vd-9Rtw" });
    renderView();
    const trigger = screen.getByRole("button", { name: "Reset password for ben@client.example" });
    const texts: string[] = [];
    const observer = new MutationObserver(() => texts.push(screen.getByRole("status").textContent ?? ""));
    observer.observe(screen.getByRole("status"), { childList: true, characterData: true, subtree: true });

    for (let round = 0; round < 2; round++) {
      await userEvent.click(trigger);
      const dialog = screen.getByRole("dialog");
      await userEvent.click(within(dialog).getByRole("button", { name: "Reset password" }));
      await userEvent.click(await within(dialog).findByRole("button", { name: "Done" }));
      await waitFor(() => expect(texts.filter((t) => t !== "")).toHaveLength(round + 1));
    }
    observer.disconnect();

    const message = "Password reset for ben@client.example.";
    expect(texts.filter((t) => t === message)).toHaveLength(2);
    expect(texts.indexOf("", texts.indexOf(message))).toBeGreaterThan(-1);
  });

  it("right-aligns the actions column", () => {
    renderView();
    expect(screen.getByRole("columnheader", { name: "Actions" })).toHaveClass("text-right");
    expect(screen.getAllByRole("cell", { name: "Actions" })[0]).toHaveClass("text-right");
  });
});

describe("ManagerTestersView deactivate and reactivate login", () => {
  it("active-tester-shows-reset-and-deactivate-named-with-email", () => {
    renderView(withDeactivated);

    const row = screen.getByRole("row", { name: /ben@client\.example/ });
    const buttons = within(row).getAllByRole("button");
    expect(buttons.map((b) => b.getAttribute("aria-label"))).toEqual([
      "Reset password for ben@client.example",
      "Deactivate login for ben@client.example",
    ]);
    expect(within(row).queryByText("Deactivated")).not.toBeInTheDocument();
  });

  it("deactivated-tester-shows-tag-in-email-cell-and-reactivate", () => {
    renderView(withDeactivated);

    const row = screen.getByRole("row", { name: /cleo@client\.example/ });
    const buttons = within(row).getAllByRole("button");
    expect(buttons.map((b) => b.getAttribute("aria-label"))).toEqual([
      "Reset password for cleo@client.example",
      "Reactivate login for cleo@client.example",
    ]);
    const emailCell = within(row).getByRole("cell", {
      name: /cleo@client\.example/,
    });
    expect(within(emailCell).getByText("Deactivated")).toBeInTheDocument();
    expect(within(emailCell).getByText("Deactivated since 2 Oct 2026")).toBeInTheDocument();
    expect(within(row).getByText("Tester")).toBeInTheDocument();
  });

  it("deactivate-confirm-calls-the-tester-route-closes-and-announces", async () => {
    const fetchMock = stubFetch(200, { deactivatedAt: "2026-10-05T10:00:00Z" });
    renderView(withDeactivated);
    const trigger = screen.getByRole("button", {
      name: "Deactivate login for ben@client.example",
    });

    await userEvent.click(trigger);
    const dialog = screen.getByRole("dialog");
    expect(
      within(dialog).getByRole("heading", {
        name: "Deactivate login for ben@client.example",
      }),
    ).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Deactivate login" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/clients/client-1/testers/t-2/deactivate", { method: "POST" });
    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent("Login deactivated for ben@client.example."),
    );
    expect(trigger).toHaveFocus();
  });

  it("reactivate-confirm-calls-the-tester-route", async () => {
    const fetchMock = stubFetch(200, {});
    renderView(withDeactivated);

    await userEvent.click(
      screen.getByRole("button", {
        name: "Reactivate login for cleo@client.example",
      }),
    );
    const dialog = screen.getByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Reactivate login" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/clients/client-1/testers/t-3/reactivate", { method: "POST" });
    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent("Login reactivated for cleo@client.example."),
    );
  });

  it("cancel-sends-nothing", async () => {
    const fetchMock = stubFetch(200, {});
    renderView(withDeactivated);

    await userEvent.click(
      screen.getByRole("button", {
        name: "Deactivate login for ben@client.example",
      }),
    );
    await userEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Cancel",
      }),
    );

    expect(fetchMock).not.toHaveBeenCalled();
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("404-shows-no-longer-exists-message", async () => {
    stubFetch(404);
    renderView(withDeactivated);

    await userEvent.click(
      screen.getByRole("button", {
        name: "Deactivate login for ben@client.example",
      }),
    );
    await userEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Deactivate login",
      }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("ben@client.example no longer exists.");
  });
});
