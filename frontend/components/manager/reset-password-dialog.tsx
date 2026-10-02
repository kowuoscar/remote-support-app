"use client";

import { forwardRef, useId, useImperativeHandle, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import { GeneratedPasswordReveal } from "@/components/manager/generated-password-reveal";
import { readErrorCode, type SubmitError } from "@/lib/api/errors";

/** Who a reset is for, and where to send it: the dialog itself knows nothing about Agents or Testers. */
export type ResetTarget = {
  /** The Agent's name or, for a Tester, their email — what the title says. */
  name: string;
  email: string;
  /** The BFF route that resets this person's password. */
  endpoint: string;
  /** Where "no longer exists" sends the Manager. */
  listLink: { href: string; label: string };
};

export type ResetPasswordDialogHandle = { open: (target: ResetTarget) => void };

const GENERIC_FAILURE =
  "Couldn't reset the password. Their old password may already have stopped working — try again to get a new one.";

/**
 * A Manager resets an Agent's or a Tester's password (manager-resets-a-password spec): Confirm,
 * then the shared one-time reveal. One instance per view, opened for whichever target was chosen
 * through its handle, so a page of thirty Testers holds one closed dialog, not thirty. On close
 * focus returns to whatever opened it, and `onReset` hands the email back so the page's own
 * always-mounted polite status can say "Password reset for {email}." — never the password.
 */
type ResetPasswordDialogProps = { onReset: (email: string) => void };

export const ResetPasswordDialog = forwardRef<ResetPasswordDialogHandle, ResetPasswordDialogProps>(function ResetPasswordDialog(
  { onReset },
  ref,
) {
  const shellRef = useRef<DialogShellHandle>(null);
  const triggerRef = useRef<HTMLElement | null>(null);
  const titleId = useId();
  const [target, setTarget] = useState<ResetTarget | null>(null);
  const [password, setPassword] = useState<string | null>(null);
  const [error, setError] = useState<SubmitError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useImperativeHandle(ref, () => ({
    open: (next) => {
      triggerRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      setTarget(next);
      setPassword(null);
      setError(null);
      setSubmitting(false);
      shellRef.current?.open();
    },
  }));

  function close() {
    shellRef.current?.close();
  }

  function handleClosed() {
    if (password && target) onReset(target.email);
    setPassword(null);
    triggerRef.current?.focus();
  }

  async function confirm() {
    if (!target) return;
    setError(null);
    setSubmitting(true);
    try {
      const response = await fetch(target.endpoint, { method: "POST" });
      if (!response.ok) {
        setError(await errorFor(response, target));
        setSubmitting(false);
        return;
      }
      const body = (await response.json()) as { password: string };
      setPassword(body.password);
      setSubmitting(false);
    } catch {
      setError({ message: GENERIC_FAILURE, field: null });
      setSubmitting(false);
    }
  }

  return (
    <>
      <DialogShell
        ref={shellRef}
        submitting={submitting}
        // Arbitrary: 420px caps the dialog on desktop, 90vw keeps a margin on a phone; no width token does both.
        widthClassName="w-[min(420px,90vw)]"
        titleId={titleId}
        onClosed={handleClosed}
      >
        {target && password ? (
          <GeneratedPasswordReveal email={target.email} password={password} mode="reset" titleId={titleId} onClose={close} />
        ) : target ? (
          <div className="flex flex-col gap-4 p-6">
            <div>
              <h2 id={titleId} className="break-words text-base font-semibold text-ink">
                Reset password for {target.name}
              </h2>
              {/* 13px: the dialog body size; the type scale has no token between text-xs and text-sm. */}
              <p className="mt-1 break-words text-[13px] text-ink-mute">
                A new password will be generated for {target.email}. Their current password stops working as soon as
                you confirm.
              </p>
            </div>

            {error ? <DialogErrorAlert message={error.message} link={error.link} /> : null}

            <div className="flex justify-end gap-2 pt-1">
              <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
                Cancel
              </Button>
              <Button type="button" variant="primary" loading={submitting} onClick={confirm}>
                Reset password
              </Button>
            </div>
          </div>
        ) : null}
      </DialogShell>
    </>
  );
});

async function errorFor(response: Response, target: ResetTarget): Promise<SubmitError> {
  if (response.status === 409 && (await readErrorCode(response)) === "AGENT_HAS_NO_LOGIN") {
    return {
      message: `${target.name} has no login to reset. This page is out of date — refresh it.`,
      field: null,
    };
  }
  if (response.status === 404) {
    return { message: `${target.name} no longer exists.`, field: null, link: target.listLink };
  }
  return { message: GENERIC_FAILURE, field: null };
}
