"use client";

import { useId, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import { GeneratedPasswordReveal } from "@/components/manager/generated-password-reveal";
import { GENERATED_PASSWORD_HINT, LoginCredentialFields } from "@/components/manager/login-credential-fields";
import { readErrorCode, usernameTakenError, type SubmitError } from "@/lib/api/errors";

/**
 * Manager gives a login to an Agent that has none (create-login-for-existing-agent ticket),
 * from the Agent's detail view. Only rendered while the Agent has no login, so the action is
 * never offered twice; the backend rejects a second login regardless, and its 409 `code` tells
 * that apart from an email already in use. Its generated password is revealed once, and `onCreated` fires when that reveal closes. Same `LoginCredentialFields` as `CreateAgentDialog`
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
  const titleId = useId();
  const [username, setUsername] = useState("");
  const [created, setCreated] = useState<{
    email: string;
    password: string;
  } | null>(null);
  const [error, setError] = useState<SubmitError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function open() {
    setUsername("");
    setCreated(null);
    setError(null);
    shellRef.current?.open();
  }

  function close() {
    shellRef.current?.close();
  }

  // The caller swaps this trigger out on `onCreated`, so it waits until the reveal is dismissed
  // (Done, Escape or backdrop): the password must never vanish while it is on screen.
  function handleClosed() {
    if (created) onCreated(created.email);
    setCreated(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const response = await fetch(`/api/agents/${agentId}/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username }),
      });

      if (!response.ok) {
        setError(await errorFor(response));
        setSubmitting(false);
        return;
      }

      const body = (await response.json()) as {
        loginUsername: string;
        password: string;
      };
      setCreated({ email: body.loginUsername, password: body.password });
      setSubmitting(false);
    } catch {
      setError({
        message: "Couldn't reach the server. Check your connection and try again.",
        field: null,
      });
      setSubmitting(false);
    }
  }

  return (
    <>
      <Button variant="row" size="sm" onClick={open}>
        Create login
      </Button>
      <DialogShell
        ref={shellRef}
        submitting={submitting}
        widthClassName="w-[min(420px,90vw)]"
        titleId={titleId}
        onClosed={handleClosed}
      >
        {created ? (
          <GeneratedPasswordReveal email={created.email} password={created.password} mode="creation" onClose={close} />
        ) : (
          <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
            <div>
              <h2 id={titleId} className="text-base font-semibold text-ink">
                Create a login for {agentName}
              </h2>
              <p className="text-[13px] text-ink-mute">{GENERATED_PASSWORD_HINT}</p>
            </div>

            {error ? <DialogErrorAlert message={error.message} link={error.link} /> : null}

            <LoginCredentialFields
              username={username}
              onUsernameChange={setUsername}
              emailPlaceholder="camille.duforet@agents.example"
              disabled={submitting}
              emailInvalid={error?.field === "email"}
              autoFocusEmail
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
        )}
      </DialogShell>
    </>
  );
}

async function errorFor(response: Response): Promise<SubmitError> {
  if (response.status === 409) {
    return (await readErrorCode(response)) === "USERNAME_TAKEN"
      ? usernameTakenError()
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
    return { message: "Enter an email for the login.", field: null };
  }
  return { message: "Couldn't create the login. Try again.", field: null };
}
