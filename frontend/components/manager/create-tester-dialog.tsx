"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { IconPlus } from "@/components/icons";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import { GeneratedPasswordReveal } from "@/components/manager/generated-password-reveal";
import { GENERATED_PASSWORD_HINT, LoginCredentialFields } from "@/components/manager/login-credential-fields";
import { readErrorCode, usernameTakenError, type SubmitError } from "@/lib/api/errors";

/**
 * Manager creates a Tester under a specific Client (manager-entity-setup ticket): lives on the
 * Client detail view, not a top-level list, since a Tester only makes sense scoped to one
 * Client. Creates the Tester's own login in the same step; the backend generates the password and the
 * dialog reveals it once (`GeneratedPasswordReveal`).
 *
 * Its 409 carries a `code` (globally-unique-usernames spec.md "The Tester path's asymmetry is
 * fixed here, not left") so a username already taken can be told apart from this Client already
 * having a primary contact, exactly as `CreateAgentLoginDialog`'s own `code` branch already does
 * for the Agent path — the taken-email wording is adopted verbatim from there.
 */
export function CreateTesterDialog({ clientId }: Readonly<{ clientId: string }>) {
  const shellRef = useRef<DialogShellHandle>(null);
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [created, setCreated] = useState<{
    email: string;
    password: string;
  } | null>(null);
  const [isPrimaryContact, setIsPrimaryContact] = useState(false);
  const [error, setError] = useState<SubmitError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function open() {
    setUsername("");
    setCreated(null);
    setIsPrimaryContact(false);
    setError(null);
    shellRef.current?.open();
  }

  function close() {
    shellRef.current?.close();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const response = await fetch(`/api/clients/${clientId}/testers`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, isPrimaryContact }),
      });

      if (!response.ok) {
        setError(await errorFor(response));
        setSubmitting(false);
        return;
      }

      const body = (await response.json()) as {
        username: string;
        password: string;
      };
      setCreated({ email: body.username, password: body.password });
      setSubmitting(false);
      router.refresh();
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
      <Button variant="primary" onClick={open}>
        <IconPlus className="h-4 w-4" />
        Add tester
      </Button>
      <DialogShell
        ref={shellRef}
        submitting={submitting}
        widthClassName="w-[min(420px,90vw)]"
        onClosed={() => setCreated(null)}
      >
        {created ? (
          <GeneratedPasswordReveal email={created.email} password={created.password} mode="creation" onClose={close} />
        ) : (
          <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
            <div>
              <h2 className="text-base font-semibold text-ink">Add a tester</h2>
              <p className="text-[13px] text-ink-mute">{GENERATED_PASSWORD_HINT}</p>
            </div>

            {error ? <DialogErrorAlert message={error.message} /> : null}

            <LoginCredentialFields
              username={username}
              onUsernameChange={setUsername}
              emailPlaceholder="tom.reyes@client.example"
              disabled={submitting}
              emailInvalid={error?.field === "email"}
              autoFocusEmail
            />

            <label className="flex items-center gap-2 text-[13px] font-medium text-ink-secondary">
              <input
                type="checkbox"
                checked={isPrimaryContact}
                onChange={(event) => setIsPrimaryContact(event.target.checked)}
                disabled={submitting}
                className="h-4 w-4 rounded border-hairline-strong text-primary focus-visible:border-primary"
              />
              Primary contact for this client
            </label>

            <div className="flex justify-end gap-2 pt-1">
              <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" loading={submitting}>
                Add tester
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
      : { message: "This client already has a primary contact.", field: null };
  }
  return { message: "Couldn't create the tester. Try again.", field: null };
}
