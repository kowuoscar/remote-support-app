import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
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
});
