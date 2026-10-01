import { act, fireEvent, render, screen } from "@testing-library/react";
import { createRef } from "react";
import { describe, expect, it } from "vitest";
import { DialogShell, type DialogShellHandle } from "./dialog-shell";

function renderShell(submitting = false) {
  const ref = createRef<DialogShellHandle>();
  const { container } = render(
    <DialogShell ref={ref} submitting={submitting}>
      <form aria-label="inner">
        <input aria-label="Name" />
      </form>
    </DialogShell>,
  );
  const dialog = container.querySelector("dialog") as HTMLDialogElement;
  return { ref, dialog };
}

describe("DialogShell", () => {
  it("keeps its children out of the DOM until opened, and removes them again on close", () => {
    const { ref, dialog } = renderShell();
    expect(dialog.querySelector("form")).toBeNull();

    act(() => ref.current?.open());
    expect(dialog.open).toBe(true);
    const input = screen.getByLabelText("Name");
    input.focus();
    expect(input).toHaveFocus();

    act(() => ref.current?.close());
    expect(dialog.querySelector("form")).toBeNull();
  });

  it("refuses Escape while submitting", () => {
    const { ref, dialog } = renderShell(true);
    act(() => ref.current?.open());
    const cancel = new Event("cancel", { cancelable: true });
    dialog.dispatchEvent(cancel);
    expect(cancel.defaultPrevented).toBe(true);
  });

  it("allows Escape when not submitting", () => {
    const { ref, dialog } = renderShell(false);
    act(() => ref.current?.open());
    const cancel = new Event("cancel", { cancelable: true });
    dialog.dispatchEvent(cancel);
    expect(cancel.defaultPrevented).toBe(false);
  });

  it("closes on a backdrop click when not submitting", () => {
    const { ref, dialog } = renderShell(false);
    act(() => ref.current?.open());
    fireEvent.click(dialog);
    expect(dialog.open).toBe(false);
    expect(dialog.querySelector("form")).toBeNull();
  });

  it("stays open on a backdrop click while submitting", () => {
    const { ref, dialog } = renderShell(true);
    act(() => ref.current?.open());
    fireEvent.click(dialog);
    expect(dialog.open).toBe(true);
    expect(dialog.querySelector("form")).not.toBeNull();
  });
});
