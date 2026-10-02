"use client";

import { useEffect, useId, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const COPIED = "Copied";
const COPY_REFUSED = "Couldn't copy — select the password and copy it yourself";

/**
 * The success step shared by the reset dialog and the three creation dialogs: the one time a
 * server-generated password is shown. The value lives in props/DOM only — never a URL, storage
 * or log — and leaves the page when the owning dialog closes (`DialogShell` unmounts its
 * children). Done is the step's only primary action.
 */
export function GeneratedPasswordReveal({
  email,
  password,
  mode,
  titleId,
  onClose,
}: Readonly<{
  email: string;
  password: string;
  mode: "creation" | "reset";
  /** The owning dialog's `aria-labelledby` target, so the dialog keeps its name on this step. */
  titleId: string;
  onClose: () => void;
}>) {
  const fieldRef = useRef<HTMLInputElement>(null);
  const id = useId();
  const [status, setStatus] = useState("");

  useEffect(() => {
    fieldRef.current?.focus();
    fieldRef.current?.select();
  }, []);

  async function copy() {
    try {
      await navigator.clipboard.writeText(password);
      setStatus(COPIED);
    } catch {
      setStatus(COPY_REFUSED);
      fieldRef.current?.focus();
      fieldRef.current?.select();
    }
  }

  return (
    <div className="flex flex-col gap-4 p-6">
      <div>
        <h2 id={titleId} className="text-base font-semibold text-ink">
          {mode === "reset" ? "Password reset" : "Login created"}
        </h2>
        {/* 13px: the type scale has no token between text-xs and text-sm; matches the dialog body copy. */}
        <p className="mt-1 break-words text-[13px] text-ink-mute">
          {mode === "reset"
            ? `${email} can now sign in with this new password. Their old one no longer works.`
            : `${email} can now sign in with this password.`}
        </p>
      </div>

      <div className="flex flex-col gap-1.5">
        {/* 13px: same as every Manager dialog field label (no token between text-xs and text-sm). */}
        <label htmlFor={`${id}-field`} className="text-[13px] font-medium text-ink">
          Generated password
        </label>
        <div className="flex items-center gap-2">
          <Input
            id={`${id}-field`}
            ref={fieldRef}
            readOnly
            value={password}
            autoComplete="off"
            spellCheck={false}
            className="tnum bg-canvas-soft tracking-widest"
          />
          <Button type="button" variant="secondary" size="sm" onClick={copy} className="shrink-0">
            Copy password
          </Button>
        </div>
        {/* 12px: helper-text size used across the app, no token for it. Empty it is sr-only, never display:none, so the live region stays in the accessibility tree. */}
        <p role="status" className="text-[12px] text-ink-mute empty:sr-only">
          {status}
        </p>
      </div>

      {/* 13px: dialog body copy, as above. */}
      <p className="text-[13px] text-ink-secondary">
        This password won&apos;t be shown again. Give it to them yourself — they can change it afterwards from their
        own menu.
      </p>

      <div className="flex justify-end pt-1">
        <Button type="button" variant="primary" onClick={onClose}>
          Done
        </Button>
      </div>
    </div>
  );
}
