"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { IconAlertTriangle, IconPlus } from "@/components/icons";
import { LoginCredentialFields } from "@/components/manager/login-credential-fields";

/**
 * Manager creates a Tester under a specific Client (manager-entity-setup ticket): lives on the
 * Client detail view, not a top-level list, since a Tester only makes sense scoped to one
 * Client. Creates the Tester's own login (username/password) in the same step, following the
 * same auth pattern as the seeded users.
 */
export function CreateTesterDialog({ clientId }: { clientId: string }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [isPrimaryContact, setIsPrimaryContact] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function open() {
    setUsername("");
    setPassword("");
    setIsPrimaryContact(false);
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
      const response = await fetch(`/api/clients/${clientId}/testers`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password, isPrimaryContact }),
      });

      if (!response.ok) {
        setError(
          response.status === 409
            ? "This client already has a primary contact, or that email is already in use."
            : "Couldn't create the tester. Try again.",
        );
        setSubmitting(false);
        return;
      }

      setSubmitting(false);
      close();
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setSubmitting(false);
    }
  }

  return (
    <>
      <Button variant="primary" onClick={open}>
        <IconPlus className="h-4 w-4" />
        Add tester
      </Button>
      <dialog
        ref={dialogRef}
        onCancel={close}
        onClick={(event) => {
          if (event.target === dialogRef.current) close();
        }}
        className="m-auto w-[min(420px,90vw)] rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
      >
        <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
          <div>
            <h2 className="text-base font-semibold text-ink">Add a tester</h2>
            <p className="text-[13px] text-ink-mute">
              Creates their login. They can sign in with this email and password right away.
            </p>
          </div>

          {error ? (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-lg bg-danger-bg px-3 py-2.5 text-[13px] text-danger"
            >
              <IconAlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          ) : null}

          <LoginCredentialFields
            username={username}
            onUsernameChange={setUsername}
            password={password}
            onPasswordChange={setPassword}
            emailPlaceholder="tom.reyes@client.example"
            disabled={submitting}
            emailInvalid={Boolean(error)}
            passwordInvalid={Boolean(error)}
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
      </dialog>
    </>
  );
}
