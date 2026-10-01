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
});
