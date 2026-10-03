import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { LoginForm } from "./login-form";

async function submitWithRouteAnswer(error: string) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => Response.json({ error }, { status: 401 })),
  );
  render(<LoginForm />);
  await userEvent.type(screen.getByLabelText("Email"), "a@example.com");
  await userEvent.type(screen.getByLabelText("Password"), "pw");
  await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
}

describe("LoginForm", () => {
  it("shows-the-deactivated-message", async () => {
    const message = "This login has been deactivated. Ask your Manager if you need access again.";
    await submitWithRouteAnswer(message);
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
  });

  it("shows-incorrect-email-or-password", async () => {
    await submitWithRouteAnswer("Incorrect email or password.");
    expect(await screen.findByRole("alert")).toHaveTextContent("Incorrect email or password.");
  });
});
