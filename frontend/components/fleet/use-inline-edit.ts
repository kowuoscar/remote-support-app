"use client";

import { useCallback, useId, useState, type FormEvent, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";

export const GENERIC_CORRECTION_ERROR = "Couldn’t update. Try again.";

/**
 * The edit-toggle behind the four Fleet correction controls (correct-a-fleet-unit spec): PATCH the
 * value, map the backend's `code` to a message at the field, refresh on success. The form it drives
 * closes on Cancel, on Escape, and after a Save, and keyboard focus returns to the row's Edit button
 * each time (the Menu Returns Focus Rule). Enter on a select saves, as it does in a text field.
 */
export function useInlineEdit({
  url,
  messages,
  onStart,
}: {
  url: string;
  /** Backend `code` to message; any other code reads {@link GENERIC_CORRECTION_ERROR}. */
  messages: Record<string, string>;
  /** Resets the control's draft value when editing starts. */
  onStart: () => void;
}) {
  const router = useRouter();
  const errorId = useId();
  const [editing, setEditing] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Set when the form closes; the Edit button takes focus as it mounts again (the form that had
  // focus has unmounted, and focus would otherwise fall to the page).
  const [returnFocus, setReturnFocus] = useState(false);
  const focusTarget = useCallback(
    (button: HTMLButtonElement | null) => {
      if (button && returnFocus) {
        button.focus();
        setReturnFocus(false);
      }
    },
    [returnFocus],
  );

  function start() {
    onStart();
    setError(null);
    setEditing(true);
  }

  function close() {
    setReturnFocus(true);
    setEditing(false);
  }

  function cancel() {
    if (!pending) close();
  }

  async function submit(event: FormEvent<HTMLFormElement>, body: unknown) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      const response = await fetch(url, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (!response.ok) {
        const failure = (await response.json().catch(() => null)) as { code?: string } | null;
        setError((failure?.code && messages[failure.code]) || GENERIC_CORRECTION_ERROR);
        setPending(false);
        return;
      }
      setPending(false);
      close();
      router.refresh();
    } catch {
      setError(GENERIC_CORRECTION_ERROR);
      setPending(false);
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      cancel();
    } else if (event.key === "Enter" && event.target instanceof HTMLSelectElement) {
      event.preventDefault();
      event.currentTarget.requestSubmit();
    }
  }

  return { editing, pending, error, setError, errorId, focusTarget, start, cancel, submit, onKeyDown };
}
