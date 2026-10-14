"use client";

import { forwardRef, useEffect, useId, useImperativeHandle, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import { readErrorCode } from "@/lib/api/errors";
import type { MeResponse } from "@/lib/api/types";

export type ChangeNameDialogHandle = { open: () => void };

const BLANK_MESSAGE = "Enter your name.";
const TOO_LONG_MESSAGE = "Your name can be at most 100 characters.";
const GENERIC_MESSAGE = "Couldn’t change your name. Try again.";

// Keyed by the backend's `code` (manager-name-api: ManagerName's NAME_BLANK / NAME_TOO_LONG).
const CODE_MESSAGES: Record<string, string> = {
  NAME_BLANK: BLANK_MESSAGE,
  NAME_TOO_LONG: TOO_LONG_MESSAGE,
};

/**
 * The dialog behind the account menu's "Change name" item (manager-changes-own-name ticket), the
 * Manager's own correction of their Manager name. Built like `ChangePasswordDialog`: opened
 * imperatively from the menu item, `DialogShell` mounted on first open only (so no page carries a
 * hidden `<form>` nobody opened), one labelled field, `DialogErrorAlert` for refusals.
 *
 * On open it reads `GET /api/me` through the browser's `/api/me` proxy and prefills the field with
 * the current name; no name set, or a read that fails, leaves it empty. A read that lands after the
 * Manager has already started typing never overwrites what they typed. A blank field is refused
 * here before any request; every refusal keeps what was typed. On `204` the dialog closes and
 * `router.refresh()` re-renders the Dashboard's chip. Unlike a password change it never signs the
 * user out: the login, password and session are untouched.
 */
export const ChangeNameDialog = forwardRef<ChangeNameDialogHandle>(function ChangeNameDialog(_props, ref) {
  const router = useRouter();
  const shellRef = useRef<DialogShellHandle>(null);
  const titleId = useId();
  // Bumped on every open so a slow read from an earlier open can't fill a later one's field.
  const openCount = useRef(0);
  const typed = useRef(false);

  const [mounted, setMounted] = useState(false);
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useImperativeHandle(ref, () => ({
    open: () => {
      setName("");
      setError(null);
      typed.current = false;
      const thisOpen = ++openCount.current;
      void readCurrentName().then((current) => {
        if (current && openCount.current === thisOpen && !typed.current) setName(current);
      });
      // First open mounts `DialogShell` and the effect below shows it; later opens call it directly.
      if (mounted) shellRef.current?.open();
      else setMounted(true);
    },
  }));

  useEffect(() => {
    if (mounted) shellRef.current?.open();
  }, [mounted]);

  function close() {
    shellRef.current?.close();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    // Refused before any request is sent; the backend trims too, so whitespace alone is blank.
    if (name.trim() === "") {
      setError(BLANK_MESSAGE);
      return;
    }

    setError(null);
    setSubmitting(true);

    try {
      const response = await fetch("/api/me/name", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name }),
      });

      if (!response.ok) {
        const code = response.status === 400 ? await readErrorCode(response) : null;
        setError((code ? CODE_MESSAGES[code] : undefined) ?? GENERIC_MESSAGE);
        setSubmitting(false);
        return;
      }

      close();
      setSubmitting(false);
      router.refresh();
    } catch {
      setError(GENERIC_MESSAGE);
      setSubmitting(false);
    }
  }

  if (!mounted) return null;

  return (
    <DialogShell ref={shellRef} submitting={submitting} titleId={titleId}>
      <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit} noValidate>
        <div>
          <h2 id={titleId} className="text-base font-semibold text-ink">
            Change name
          </h2>
          <p className="text-label text-ink-mute">This is the name your dashboard shows next to your role.</p>
        </div>

        {error ? <DialogErrorAlert message={error} /> : null}

        <label className="flex flex-col gap-1.5 text-label font-medium text-ink-secondary">
          Name
          <Input
            type="text"
            name="name"
            autoComplete="name"
            autoFocus
            value={name}
            onChange={(event) => {
              typed.current = true;
              setName(event.target.value);
            }}
            disabled={submitting}
            invalid={error !== null}
          />
        </label>

        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" loading={submitting}>
            Change name
          </Button>
        </div>
      </form>
    </DialogShell>
  );
});

/** The caller's stored Manager name, or `null` when none is set or the read fails. */
async function readCurrentName(): Promise<string | null> {
  try {
    const response = await fetch("/api/me");
    if (!response.ok) return null;
    const body = (await response.json()) as Pick<MeResponse, "name">;
    return typeof body.name === "string" && body.name !== "" ? body.name : null;
  } catch {
    return null;
  }
}
