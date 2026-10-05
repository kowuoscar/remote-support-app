import { useRef } from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { LoginActivationDialog, type LoginActivationDialogHandle } from "./login-activation-dialog";

const target = {
  name: "Camille Duforet",
  email: "camille@agents.example",
  endpoint: "/api/agents/agent-1/login/deactivate",
  listLink: { href: "/manager/agents", label: "Back to the Agents list" },
};

function Host({ onChanged }: Readonly<{ onChanged: (email: string) => void }>) {
  const ref = useRef<LoginActivationDialogHandle>(null);
  return (
    <>
      <button type="button" onClick={() => ref.current?.open(target)}>
        Open dialog
      </button>
      <LoginActivationDialog ref={ref} mode="deactivate" onChanged={onChanged} />
    </>
  );
}

async function openDialog(onChanged = vi.fn()) {
  render(<Host onChanged={onChanged} />);
  const trigger = screen.getByRole("button", { name: "Open dialog" });
  await userEvent.click(trigger);
  return { trigger, dialog: screen.getByRole("dialog"), onChanged };
}

async function confirm(dialog: HTMLElement) {
  await userEvent.click(within(dialog).getByRole("button", { name: "Deactivate login" }));
}

describe("LoginActivationDialog (deactivate)", () => {
  beforeEach(() => mockRouter.refresh.mockReset());

  it("asks for confirmation, naming the person and saying what stays", async () => {
    const { dialog } = await openDialog();

    expect(within(dialog).getByRole("heading", { name: "Deactivate login for Camille Duforet" })).toBeInTheDocument();
    expect(
      within(dialog).getByText(
        "camille@agents.example will no longer be able to sign in, and is signed out at once. Their record, requests and invoices stay as they are. You can reactivate this login later.",
      ),
    ).toBeInTheDocument();
  });

  it("cancel-sends-nothing", async () => {
    const fetchMock = stubFetch(200, { deactivatedAt: "2026-10-02T09:14:00Z" });
    const { dialog, onChanged } = await openDialog();

    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));

    expect(fetchMock).not.toHaveBeenCalled();
    expect(onChanged).not.toHaveBeenCalled();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("200-closes-refreshes-and-announces", async () => {
    const fetchMock = stubFetch(200, { deactivatedAt: "2026-10-02T09:14:00Z" });
    const { dialog, onChanged } = await openDialog();

    await confirm(dialog);

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/agents/agent-1/login/deactivate");
    expect(init.method).toBe("POST");
    expect(mockRouter.refresh).toHaveBeenCalledTimes(1);
    expect(onChanged).toHaveBeenCalledExactlyOnceWith("camille@agents.example");
  });

  it("focus-returns-to-trigger", async () => {
    stubFetch(200, { deactivatedAt: "2026-10-02T09:14:00Z" });
    const { trigger, dialog } = await openDialog();

    await confirm(dialog);

    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it("agent-has-no-login-shows-stale-page-message", async () => {
    stubFetch(409, { code: "AGENT_HAS_NO_LOGIN" });
    const { dialog, onChanged } = await openDialog();

    await confirm(dialog);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Camille Duforet has no login. This page is out of date — refresh it.",
    );
    expect(onChanged).not.toHaveBeenCalled();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("404-shows-no-longer-exists-message", async () => {
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
  ])("other-failure-shows-generic-message on %s", async (_name, stub) => {
    stub();
    const { dialog } = await openDialog();

    await confirm(dialog);

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't change this login. Try again.");
    expect(within(dialog).getByRole("button", { name: "Deactivate login" })).toBeEnabled();
  });

  it("keeps focus on the confirm button while the request is in flight, and ignores a repeat", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    const { dialog } = await openDialog();
    const button = within(dialog).getByRole("button", { name: "Deactivate login" });

    await userEvent.click(button);
    await userEvent.click(button);
    await userEvent.keyboard("{Enter}");

    expect(button).toHaveFocus();
    expect(button).toHaveAttribute("aria-busy", "true");
    expect(button).toHaveAttribute("aria-disabled", "true");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    respond(500);
    await screen.findByRole("alert");
  });

  it("keeps focus on the confirm button after a failed request", async () => {
    stubFetch(500);
    const { dialog } = await openDialog();
    const button = within(dialog).getByRole("button", { name: "Deactivate login" });

    await userEvent.click(button);

    await screen.findByRole("alert");
    expect(button).toHaveFocus();
    expect(button).toHaveAttribute("aria-busy", "false");
  });
});
