"use client";

import { useId, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import { LoginCredentialFields } from "@/components/manager/login-credential-fields";
import { readErrorCode } from "@/lib/api/errors";

type SubmitError = {
  message: string;
  field: "email" | "password" | null;
  /** A way out when retrying can't help, e.g. the Agent is gone. */
  link?: { href: string; label: string };
};

/**
 * Manager gives a login to an Agent that has none (create-login-for-existing-agent ticket),
 * from the Agent's detail view. Only rendered while the Agent has no login, so the action is
 * never offered twice; the backend rejects a second login regardless, and its 409 `code` tells
 * that apart from an email already in use. Same `LoginCredentialFields` as `CreateAgentDialog`
 * and `CreateTesterDialog`. `onCreated` hands the new login's email back to the caller, which
 * owns what happens to focus once this trigger unmounts.
 */
export function CreateAgentLoginDialog({
  agentId,
  agentName,
  onCreated,
}: Readonly<{
  agentId: string;
  agentName: string;
  onCreated: (loginUsername: string) => void;
}>) {
  const shellRef = useRef<DialogShellHandle>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const titleId = useId();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<SubmitError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function open() {
    setUsername("");
    setPassword("");
    setError(null);
    shellRef.current?.open();
  }

  function close() {
    shellRef.current?.close();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // `required` accepts a password of only spaces; the backend rejects it as blank.
    if (password.trim() === "") {
      setError({ message: "The temporary password can't be only spaces.", field: "password" });
      passwordRef.current?.focus();
      return;
    }
    setError(null);
    setSubmitting(true);

    try {
      const response = await fetch(`/api/agents/${agentId}/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });

      if (!response.ok) {
        setError(await errorFor(response));
        setSubmitting(false);
        return;
      }

      const created = (await response.json()) as { loginUsername: string };
      setSubmitting(false);
      close();
      onCreated(created.loginUsername);
    } catch {
      setError({ message: "Couldn't reach the server. Check your connection and try again.", field: null });
      setSubmitting(false);
    }
  }

  return (
    <>
      <Button variant="row" size="sm" onClick={open}>
        Create login
      </Button>
      <DialogShell ref={shellRef} submitting={submitting} widthClassName="w-[min(420px,90vw)]" titleId={titleId}>
        <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
          <div>
            <h2 id={titleId} className="text-base font-semibold text-ink">Create a login for {agentName}</h2>
            <p className="text-[13px] text-ink-mute">
              They can sign in with this email and password right away.
            </p>
          </div>

          {error ? <DialogErrorAlert message={error.message} link={error.link} /> : null}

          <LoginCredentialFields
            username={username}
            onUsernameChange={setUsername}
            password={password}
            onPasswordChange={setPassword}
            emailPlaceholder="camille.duforet@agents.example"
            disabled={submitting}
            emailInvalid={error?.field === "email"}
            passwordInvalid={error?.field === "password"}
            autoFocusEmail
            passwordRef={passwordRef}
          />

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              Create login
            </Button>
          </div>
        </form>
      </DialogShell>
    </>
  );
}

async function errorFor(response: Response): Promise<SubmitError> {
  if (response.status === 409) {
    return (await readErrorCode(response)) === "USERNAME_TAKEN"
      ? { message: "That email is already in use. Choose another one and try again.", field: "email" }
      : {
          message: "This agent already has a login. Refresh the page to see it.",
          field: null,
        };
  }
  if (response.status === 404) {
    return {
      message: "This agent no longer exists.",
      field: null,
      link: { href: "/manager/agents", label: "Back to the Agents list" },
    };
  }
  if (response.status === 400) {
    return { message: "Enter both an email and a temporary password.", field: null };
  }
  return { message: "Couldn't create the login. Try again.", field: null };
}
