"use client";

import { forwardRef, useEffect, useId, useImperativeHandle, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import { readErrorCode } from "@/lib/api/errors";

export type ChangePasswordDialogHandle = { open: () => void };

type FieldName = "current" | "new" | "confirmNew";

type ChangePasswordError = { message: string; field: FieldName | null };

const MISMATCH_ERROR: ChangePasswordError = {
  message: "New password and confirmation don't match.",
  field: "confirmNew",
};

const GENERIC_ERROR: ChangePasswordError = {
  message: "Couldn't change the password. Try again.",
  field: null,
};

// Keyed by the backend's `code` (ChangePasswordRefusedException.Reason / PASSWORD_TOO_SHORT —
// change-own-password-endpoint and password-minimum-length tickets), so this dialog can point at
// the right field for each distinct refusal rather than showing one generic message for all three.
const CODE_ERRORS: Record<string, ChangePasswordError> = {
  WRONG_CURRENT_PASSWORD: { message: "That's not your current password. Try again.", field: "current" },
  PASSWORD_UNCHANGED: {
    message: "Choose a new password that's different from your current one.",
    field: "new",
  },
  PASSWORD_TOO_SHORT: { message: "Your new password needs to be at least 8 characters.", field: "new" },
};

/**
 * The dialog behind the viewer chip menu's "Change password" item (change-password-dialog
 * ticket). Opened imperatively, the same shape `ArchiveCarrierDialog` already uses, because its
 * trigger is a `menuitem` inside `ViewerMenu`, not a button of its own. Three password fields
 * follow `LoginCredentialFields`' field shape — label above `Input`, explicit `autoComplete`
 * hints — without reusing that component: it pairs an email with a single password, and this form
 * has three passwords and no email (spec.md `## Decisions taken`). The confirm-new field is
 * checked in the browser only and never sent.
 *
 * On success the dialog does not just close: per the human's session decision (spec.md
 * `## Decisions taken`, "Changing a password signs the user out of this browser"), it drops the
 * session through the existing sign-out route (`DELETE /api/session`) and sends the user to
 * sign-in carrying a confirmation, so they prove the new password immediately. Every refusal
 * renders through `DialogErrorAlert` with the typed values left alone — none of them signs the
 * user out or navigates away, since every refusal from `/api/me/password` is a `400`, never a
 * `401` (spec.md Constraints).
 *
 * `DialogShell` renders its children — this component's whole `<form>` — into the DOM
 * unconditionally, same as every other `DialogShell` consumer; fine for those, since each is
 * scoped to the one page that renders it. This dialog's trigger (`ViewerActions`, inside `TopBar`)
 * is on every page of every console, so mounting the form unconditionally put a hidden `<form>`
 * ahead of every page's own forms in document order — exactly what broke
 * `agent-standing-amounts-and-invoice-generation.spec.ts`'s index-based
 * `page.locator("form").nth(0)` on a page this feature never touches. `mounted` defers rendering
 * `DialogShell` at all until `open()` is first called, so a page nobody has changed a password
 * from keeps exactly the DOM it had before this ticket.
 */
export const ChangePasswordDialog = forwardRef<ChangePasswordDialogHandle>(function ChangePasswordDialog(
  _props,
  ref,
) {
  const router = useRouter();
  const shellRef = useRef<DialogShellHandle>(null);
  const currentRef = useRef<HTMLInputElement>(null);
  const newRef = useRef<HTMLInputElement>(null);
  const confirmRef = useRef<HTMLInputElement>(null);
  const titleId = useId();

  const [mounted, setMounted] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmNewPassword, setConfirmNewPassword] = useState("");
  const [error, setError] = useState<ChangePasswordError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useImperativeHandle(ref, () => ({
    open: () => {
      setCurrentPassword("");
      setNewPassword("");
      setConfirmNewPassword("");
      setError(null);
      // First open of this session: mount `DialogShell` and let the effect below call its
      // `showModal()` once the `<dialog>` element exists. Every later open: it's already mounted
      // from before (closing never unmounts it), so `shellRef` is already populated and this can
      // call it directly — `setMounted(true)` again would be a same-value no-op that never
      // re-triggers the mount effect.
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

  function focusField(field: FieldName | null) {
    if (field === "current") currentRef.current?.focus();
    else if (field === "new") newRef.current?.focus();
    else if (field === "confirmNew") confirmRef.current?.focus();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    // Caught before any request is sent (acceptance criteria): the backend never sees the
    // confirm-new field at all, so a mismatch here has nothing to do with the network.
    if (newPassword !== confirmNewPassword) {
      setError(MISMATCH_ERROR);
      confirmRef.current?.focus();
      return;
    }

    setError(null);
    setSubmitting(true);

    try {
      const response = await fetch("/api/me/password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentPassword, newPassword }),
      });

      if (!response.ok) {
        const submitError = await errorFor(response);
        setError(submitError);
        focusField(submitError.field);
        setSubmitting(false);
        return;
      }

      await fetch("/api/session", { method: "DELETE" });
      close();
      router.push("/login?passwordChanged=1");
      router.refresh();
    } catch {
      setError({ message: "Couldn't reach the server. Check your connection and try again.", field: null });
      setSubmitting(false);
    }
  }

  if (!mounted) return null;

  return (
    <DialogShell ref={shellRef} submitting={submitting} widthClassName="w-[min(440px,90vw)]" titleId={titleId}>
      <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
        <div>
          <h2 id={titleId} className="text-base font-semibold text-ink">
            Change password
          </h2>
          <p className="text-[13px] text-ink-mute">Enter your current password and choose a new one.</p>
        </div>

        {error ? <DialogErrorAlert message={error.message} /> : null}

        <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
          Current password
          <Input
            ref={currentRef}
            type="password"
            required
            autoComplete="current-password"
            autoFocus
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
            disabled={submitting}
            invalid={error?.field === "current"}
            placeholder="••••••••"
          />
        </label>

        <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
          New password
          <Input
            ref={newRef}
            type="password"
            required
            autoComplete="new-password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            disabled={submitting}
            invalid={error?.field === "new"}
            placeholder="••••••••"
          />
        </label>

        <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
          Confirm new password
          <Input
            ref={confirmRef}
            type="password"
            required
            autoComplete="new-password"
            value={confirmNewPassword}
            onChange={(event) => setConfirmNewPassword(event.target.value)}
            disabled={submitting}
            invalid={error?.field === "confirmNew"}
            placeholder="••••••••"
          />
        </label>

        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" loading={submitting}>
            Change password
          </Button>
        </div>
      </form>
    </DialogShell>
  );
});

async function errorFor(response: Response): Promise<ChangePasswordError> {
  if (response.status === 400) {
    const code = await readErrorCode(response);
    return (code ? CODE_ERRORS[code] : undefined) ?? GENERIC_ERROR;
  }
  return GENERIC_ERROR;
}
