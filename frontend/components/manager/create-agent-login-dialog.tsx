"use client";

import { useId, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconAlertTriangle } from "@/components/icons";

/**
 * Manager gives a login to an Agent that has none (create-login-for-existing-agent ticket),
 * from the Agent's detail view. Only rendered while the Agent has no login, so the action is
 * never offered twice; the backend rejects a second login regardless. Same Email and Temporary
 * password fields, types, placeholders and copy as `CreateAgentDialog` and `CreateTesterDialog`.
 */
export function CreateAgentLoginDialog({ agentId, agentName }: { agentId: string; agentName: string }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<{ message: string; emailConflict: boolean } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function open() {
    setUsername("");
    setPassword("");
    setError(null);
    dialogRef.current?.showModal();
  }

  function close() {
    dialogRef.current?.close();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const response = await fetch(`/api/agents/${agentId}/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });

      if (!response.ok) {
        setError({
          emailConflict: response.status === 409,
          message:
            response.status === 409
              ? "That email is already in use, or this agent already has a login. Choose another email and try again."
              : response.status === 404
                ? "This agent no longer exists."
                : response.status === 400
                  ? "Enter both an email and a temporary password."
                  : "Couldn't create the login. Try again.",
        });
        setSubmitting(false);
        return;
      }

      setSubmitting(false);
      close();
      router.refresh();
    } catch {
      setError({ message: "Couldn't reach the server. Check your connection and try again.", emailConflict: false });
      setSubmitting(false);
    }
  }

  return (
    <>
      <Button variant="row" size="sm" onClick={open}>
        Create login
      </Button>
      <dialog
        ref={dialogRef}
        aria-labelledby={titleId}
        onCancel={close}
        onClick={(event) => {
          if (event.target === dialogRef.current) close();
        }}
        className="m-auto w-[min(420px,90vw)] rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
      >
        <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
          <div>
            <h2 id={titleId} className="text-base font-semibold text-ink">Create a login for {agentName}</h2>
            <p className="text-[13px] text-ink-mute">
              They can sign in with this email and password right away.
            </p>
          </div>

          {error ? (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-lg bg-danger-bg px-3 py-2.5 text-[13px] text-danger"
            >
              <IconAlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{error.message}</span>
            </div>
          ) : null}

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Email
            <Input
              type="email"
              name="username"
              autoFocus
              required
              autoComplete="off"
              spellCheck={false}
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              disabled={submitting}
              invalid={error?.emailConflict ?? false}
              placeholder="tom.reyes@client.example"
            />
          </label>

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Temporary password
            <Input
              type="password"
              name="password"
              required
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              disabled={submitting}
              placeholder="••••••••"
            />
          </label>

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              Create login
            </Button>
          </div>
        </form>
      </dialog>
    </>
  );
}
