"use client";

import { useId, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconPlus } from "@/components/icons";
import { DialogErrorAlert } from "@/components/manager/dialog-error-alert";
import { DialogShell, type DialogShellHandle } from "@/components/manager/dialog-shell";
import { LoginCredentialFields } from "@/components/manager/login-credential-fields";
import { readErrorCode, usernameTakenError, type SubmitError } from "@/lib/api/errors";
import { COUNTRIES, type Country } from "@/lib/api/types";

function errorFor(code: string | null, status: number): SubmitError {
  if (code === "USERNAME_TAKEN") {
    return usernameTakenError();
  }
  if (status === 400) {
    return {
      message: "Check the agent's details — every field is required, and salary can't be negative.",
      field: null,
    };
  }
  return { message: "Couldn't create the agent. Try again.", field: null };
}

/**
 * Manager creates an Agent (manager-entity-setup ticket). Currency is never a field the Manager
 * fills in — it's shown read-only, derived live from the selected country (spec.md: "a country
 * (which fixes their currency)"), so there's no way to submit a mismatched pair.
 *
 * The Agent's login is created in the same step, from an email and temporary password
 * (create-agent-with-login ticket): the same `LoginCredentialFields` as `CreateTesterDialog`,
 * grouped under a "Sign-in" sub-heading, so no Agent a Manager creates is ever unable to sign in.
 */
export function CreateAgentDialog() {
  const shellRef = useRef<DialogShellHandle>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const signInHintId = useId();
  const router = useRouter();
  const [name, setName] = useState("");
  const [country, setCountry] = useState<Country>(COUNTRIES[0].value);
  const [salaryAmount, setSalaryAmount] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<SubmitError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const currency = COUNTRIES.find((c) => c.value === country)?.currency ?? "";

  function open() {
    setName("");
    setCountry(COUNTRIES[0].value);
    setSalaryAmount("");
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
      const response = await fetch("/api/agents", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, country, salaryAmount: Number(salaryAmount), username, password }),
      });

      if (!response.ok) {
        const code = response.status === 409 ? await readErrorCode(response) : null;
        setError(errorFor(code, response.status));
        setSubmitting(false);
        return;
      }

      setSubmitting(false);
      close();
      router.refresh();
    } catch {
      setError({ message: "Couldn't reach the server. Check your connection and try again.", field: null });
      setSubmitting(false);
    }
  }

  return (
    <>
      <Button variant="primary" onClick={open}>
        <IconPlus className="h-4 w-4" />
        Add agent
      </Button>
      <DialogShell ref={shellRef} submitting={submitting}>
        <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
          <div>
            <h2 className="text-base font-semibold text-ink">Add an agent</h2>
            <p className="text-[13px] text-ink-mute">
              Currency follows the agent&rsquo;s country automatically.
            </p>
          </div>

          {error ? <DialogErrorAlert message={error.message} /> : null}

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Agent name
            <Input
              autoFocus
              required
              value={name}
              onChange={(event) => setName(event.target.value)}
              disabled={submitting}
              placeholder="Camille Duforet"
            />
          </label>

          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Country
            <select
              required
              value={country}
              onChange={(event) => setCountry(event.target.value as Country)}
              disabled={submitting}
              className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-70"
            >
              {COUNTRIES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </label>

          <div className="grid grid-cols-2 gap-3">
            <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
              Standing monthly salary
              <Input
                type="number"
                min="0"
                step="0.01"
                required
                value={salaryAmount}
                onChange={(event) => setSalaryAmount(event.target.value)}
                disabled={submitting}
                placeholder="2400.00"
              />
            </label>
            <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
              Currency
              <div className="flex h-9 items-center rounded-lg border border-hairline-strong bg-canvas-soft px-3 text-sm text-ink-mute">
                {currency}
              </div>
            </label>
          </div>

          <fieldset aria-describedby={signInHintId} className="min-w-0 border-t border-hairline pt-4">
            {/* Floated so it lays out as an ordinary heading instead of notching the top border. */}
            <legend className="float-left w-full text-sm font-semibold text-ink">Sign-in</legend>
            <p id={signInHintId} className="clear-left pt-0.5 text-[13px] text-ink-mute">
              They can sign in with this email and password right away.
            </p>

            <div className="mt-4 flex flex-col gap-4">
              <LoginCredentialFields
                username={username}
                onUsernameChange={setUsername}
                password={password}
                onPasswordChange={setPassword}
                emailPlaceholder="camille.duforet@agents.example"
                disabled={submitting}
                emailInvalid={error?.field === "email"}
                passwordInvalid={error?.field === "password"}
                passwordRef={passwordRef}
              />
            </div>
          </fieldset>

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              Add agent
            </Button>
          </div>
        </form>
      </DialogShell>
    </>
  );
}
