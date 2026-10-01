import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { GeneratedPasswordReveal } from "./generated-password-reveal";

const PASSWORD = "k7Qm-x2Vd-9Rtw";

function stubClipboard(writeText: (text: string) => Promise<void>) {
  Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });
}

function renderReveal(mode: "creation" | "reset" = "creation", onClose = vi.fn()) {
  render(<GeneratedPasswordReveal email="camille@agents.example" password={PASSWORD} mode={mode} onClose={onClose} />);
  return { onClose };
}

afterEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

describe("GeneratedPasswordReveal", () => {
  it("shows the value read-only, focused and selected on mount", () => {
    renderReveal();
    const field = screen.getByLabelText("Generated password") as HTMLInputElement;
    expect(field.value).toBe(PASSWORD);
    expect(field).toHaveAttribute("readonly");
    expect(field).toHaveAttribute("autocomplete", "off");
    expect(field).toHaveAttribute("spellcheck", "false");
    expect(field).toHaveFocus();
    expect(field.selectionStart).toBe(0);
    expect(field.selectionEnd).toBe(PASSWORD.length);
  });

  it("announces Copied after writing the exact value to the clipboard", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubClipboard(writeText);
    renderReveal();
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Copy password" }));
    });
    expect(writeText).toHaveBeenCalledWith(PASSWORD);
    expect(screen.getByRole("status")).toHaveTextContent("Copied");
  });

  it("announces the fallback when the clipboard refuses", async () => {
    stubClipboard(vi.fn().mockRejectedValue(new Error("denied")));
    renderReveal();
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Copy password" }));
    });
    expect(screen.getByRole("status")).toHaveTextContent(
      "Couldn't copy — select the password and copy it yourself",
    );
  });

  it("calls the close callback from Done, the only primary action, and warns it won't be shown again", () => {
    const { onClose } = renderReveal();
    expect(screen.getByText(/This password won't be shown again/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Done" }));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(screen.getAllByRole("button").map((b) => b.textContent)).toEqual(["Copy password", "Done"]);
  });

  it("words the creation and reset variants", () => {
    const { unmount } = render(
      <GeneratedPasswordReveal email="a@b.example" password={PASSWORD} mode="creation" onClose={vi.fn()} />,
    );
    expect(screen.getByText("a@b.example can now sign in with this password.")).toBeInTheDocument();
    unmount();
    render(<GeneratedPasswordReveal email="a@b.example" password={PASSWORD} mode="reset" onClose={vi.fn()} />);
    expect(
      screen.getByText("a@b.example can now sign in with this new password. Their old one no longer works."),
    ).toBeInTheDocument();
  });

  it("leaves localStorage and sessionStorage empty", () => {
    renderReveal();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
