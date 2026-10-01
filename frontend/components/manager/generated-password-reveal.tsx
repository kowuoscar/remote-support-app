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
  onClose,
}: Readonly<{
  email: string;
  password: string;
  mode: "creation" | "reset";
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
        <h2 id={id} className="text-base font-semibold text-ink">
          {mode === "reset" ? "Password reset" : "Login created"}
        </h2>
        <p className="text-[13px] text-ink-mute">
          {mode === "reset"
            ? `${email} can now sign in with this new password. Their old one no longer works.`
            : `${email} can now sign in with this password.`}
        </p>
      </div>

      <div className="flex flex-col gap-1.5">
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
            className="tnum bg-canvas-soft font-mono tracking-widest"
          />
          <Button type="button" variant="secondary" size="sm" onClick={copy} className="shrink-0">
            Copy password
          </Button>
        </div>
        <p role="status" className="text-[12px] text-ink-mute empty:hidden">
          {status}
        </p>
      </div>

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
