"use client";

import { forwardRef, useId, useImperativeHandle, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import { readErrorCode, type SubmitError } from "@/lib/api/errors";

/** Who a Login change is for, and where to send it: the dialog knows nothing about Agents or Testers. */
export type LoginActivationTarget = {
  /** The Agent's name or, for a Tester, their email — what the title says. */
  name: string;
  email: string;
  /** The BFF route that deactivates or reactivates this person's Login. */
  endpoint: string;
  /** Where "no longer exists" sends the Manager. */
  listLink: { href: string; label: string };
};

export type LoginActivationDialogHandle = { open: (target: LoginActivationTarget) => void };

export type LoginActivationDialogProps = { onChanged: (email: string) => void };

type Mode = "deactivate" | "reactivate";

const COPY: Record<Mode, { action: string; body: (email: string) => string }> = {
  deactivate: {
    action: "Deactivate login",
    body: (email) =>
      `${email} will no longer be able to sign in, and is signed out at once. Their record, requests and invoices stay as they are. You can reactivate this login later.`,
  },
  reactivate: {
    action: "Reactivate login",
    body: (email) =>
      `${email} will be able to sign in again with their existing password. If they don't know it, reset it afterwards.`,
  },
};

const GENERIC_FAILURE = "Couldn't change this login. Try again.";

/**
 * The confirm step a Manager answers before a Login is switched off or back on (deactivate-a-login
 * spec). One instance per view, opened for whichever target was chosen through its handle, so a
 * page of thirty Testers holds one closed dialog, not thirty. Used through DeactivateLoginDialog
 * and ReactivateLoginDialog, the two names the views know. On 200 it closes, refreshes the page
 * and hands the email back through `onChanged` so the view's own always-mounted polite status can
 * say what happened; focus returns to whatever opened it.
 */
export const LoginActivationDialog = forwardRef<
  LoginActivationDialogHandle,
  LoginActivationDialogProps & { mode: Mode }
>(function LoginActivationDialog({ mode, onChanged }, ref) {
  const router = useRouter();
  const shellRef = useRef<DialogShellHandle>(null);
  const triggerRef = useRef<HTMLElement | null>(null);
  const changedRef = useRef(false);
  const titleId = useId();
  const [target, setTarget] = useState<LoginActivationTarget | null>(null);
  const [error, setError] = useState<SubmitError | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const copy = COPY[mode];

  useImperativeHandle(ref, () => ({
    open: (next) => {
      triggerRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      changedRef.current = false;
      setTarget(next);
      setError(null);
      setSubmitting(false);
      shellRef.current?.open();
    },
  }));

  function close() {
    shellRef.current?.close();
  }

  function handleClosed() {
    triggerRef.current?.focus();
    if (changedRef.current && target) onChanged(target.email);
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
      changedRef.current = true;
      router.refresh();
      close();
    } catch {
      setError({ message: GENERIC_FAILURE, field: null });
      setSubmitting(false);
    }
  }

  return (
    <DialogShell
      ref={shellRef}
      submitting={submitting}
      // Arbitrary: 420px caps the dialog on desktop, 90vw keeps a margin on a phone; no width token does both.
      widthClassName="w-[min(420px,90vw)]"
      titleId={titleId}
      onClosed={handleClosed}
    >
      {target ? (
        <div className="flex flex-col gap-4 p-6">
          <div>
            <h2 id={titleId} className="break-words text-base font-semibold text-ink">
              {copy.action} for {target.name}
            </h2>
            {/* 13px: the dialog body size; the type scale has no token between text-xs and text-sm. */}
            <p className="mt-1 break-words text-[13px] text-ink-mute">{copy.body(target.email)}</p>
          </div>

          {error ? <DialogErrorAlert message={error.message} link={error.link} /> : null}

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button type="button" variant="primary" loading={submitting} onClick={confirm}>
              {copy.action}
            </Button>
          </div>
        </div>
      ) : null}
    </DialogShell>
  );
});

async function errorFor(response: Response, target: LoginActivationTarget): Promise<SubmitError> {
  if (response.status === 409 && (await readErrorCode(response)) === "AGENT_HAS_NO_LOGIN") {
    return { message: `${target.name} has no login. This page is out of date — refresh it.`, field: null };
  }
  if (response.status === 404) {
    return { message: `${target.name} no longer exists.`, field: null, link: target.listLink };
  }
  return { message: GENERIC_FAILURE, field: null };
}
