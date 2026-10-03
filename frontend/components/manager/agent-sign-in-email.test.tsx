import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import { AgentSignInEmail } from "./agent-sign-in-email";

describe("AgentSignInEmail", () => {
  it("offers Reset password beside the email when the Agent has a login", () => {
    render(<AgentSignInEmail agentId="agent-1" agentName="Camille" loginUsername="camille@agents.example" />);

    expect(screen.getByText("camille@agents.example")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reset password" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create login" })).not.toBeInTheDocument();
  });

  it("offers only Create login when the Agent has no login", () => {
    render(<AgentSignInEmail agentId="agent-1" agentName="Camille" loginUsername={null} />);

    expect(screen.getByRole("button", { name: "Create login" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reset password" })).not.toBeInTheDocument();
  });

  it("announces the reset in its one status region, without the password, and returns focus to the action", async () => {
    stubFetch(200, { password: "k7Qm-x2Vd-9Rtw" });
    render(<AgentSignInEmail agentId="agent-1" agentName="Camille" loginUsername="camille@agents.example" />);
    const trigger = screen.getByRole("button", { name: "Reset password" });

    await userEvent.click(trigger);
    const dialog = screen.getByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Reset password" }));
    await userEvent.click(await within(dialog).findByRole("button", { name: "Done" }));

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Password reset for camille@agents.example."));
    expect(screen.getByRole("status")).not.toHaveTextContent("k7Qm");
    expect(trigger).toHaveFocus();
    expect(document.body.innerHTML).not.toContain("k7Qm-x2Vd-9Rtw");
  });

  it("active-login-shows-reset-and-deactivate", () => {
    render(<AgentSignInEmail agentId="agent-1" agentName="Camille" loginUsername="camille@agents.example" />);

    expect(screen.getByRole("button", { name: "Reset password" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Deactivate login" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reactivate login" })).not.toBeInTheDocument();
    expect(screen.queryByText("Deactivated")).not.toBeInTheDocument();
  });

  it("deactivated-login-shows-tag-reset-and-reactivate-and-no-create", () => {
    render(
      <AgentSignInEmail
        agentId="agent-1"
        agentName="Camille"
        loginUsername="camille@agents.example"
        loginDeactivatedAt="2026-10-02T09:14:00Z"
      />,
    );

    const tag = screen.getByText("Deactivated");
    expect(tag).toBeInTheDocument();
    expect(screen.getByText("Deactivated since 2 Oct 2026")).toBeInTheDocument();
    expect(tag.closest("[title]")).toHaveAttribute("title", "Deactivated since 2 Oct 2026");
    expect(screen.getByText("camille@agents.example")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reset password" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reactivate login" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Deactivate login" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create login" })).not.toBeInTheDocument();
  });

  it("login-less-agent-shows-create-login-only", () => {
    render(<AgentSignInEmail agentId="agent-1" agentName="Camille" loginUsername={null} />);

    expect(screen.getByRole("button", { name: "Create login" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reset password" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Deactivate login" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reactivate login" })).not.toBeInTheDocument();
  });

  it("announces a deactivation in its one status region and returns focus to the action", async () => {
    stubFetch(200, { deactivatedAt: "2026-10-02T09:14:00Z" });
    render(<AgentSignInEmail agentId="agent-1" agentName="Camille" loginUsername="camille@agents.example" />);
    const trigger = screen.getByRole("button", { name: "Deactivate login" });

    await userEvent.click(trigger);
    const dialog = screen.getByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Deactivate login" }));

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Login deactivated for camille@agents.example."));
    expect(trigger).toHaveFocus();
  });

  it("announces a reactivation in its one status region", async () => {
    stubFetch(200, { deactivatedAt: null });
    render(
      <AgentSignInEmail
        agentId="agent-1"
        agentName="Camille"
        loginUsername="camille@agents.example"
        loginDeactivatedAt="2026-10-02T09:14:00Z"
      />,
    );
    const trigger = screen.getByRole("button", { name: "Reactivate login" });

    await userEvent.click(trigger);
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Reactivate login" }));

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Login reactivated for camille@agents.example."));
    expect(trigger).toHaveFocus();
  });
});
