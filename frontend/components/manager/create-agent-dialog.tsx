"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconAlertTriangle, IconPlus } from "@/components/icons";
import { COUNTRIES, type Country } from "@/lib/api/types";

/**
 * Manager creates an Agent (manager-entity-setup ticket). Currency is never a field the Manager
 * fills in — it's shown read-only, derived live from the selected country (spec.md: "a country
 * (which fixes their currency)"), so there's no way to submit a mismatched pair.
 */
export function CreateAgentDialog() {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const [name, setName] = useState("");
  const [country, setCountry] = useState<Country>(COUNTRIES[0].value);
  const [salaryAmount, setSalaryAmount] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const currency = COUNTRIES.find((c) => c.value === country)?.currency ?? "";

  function open() {
    setName("");
    setCountry(COUNTRIES[0].value);
    setSalaryAmount("");
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
      const response = await fetch("/api/agents", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, country, salaryAmount: Number(salaryAmount) }),
      });

      if (!response.ok) {
        setError(
          response.status === 400
            ? "Check the agent's name and salary — salary can't be negative."
            : "Couldn't create the agent. Try again.",
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
        Add agent
      </Button>
      <dialog
        ref={dialogRef}
        onCancel={close}
        onClick={(event) => {
          if (event.target === dialogRef.current) close();
        }}
        className="m-auto w-[min(440px,90vw)] rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
      >
        <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
          <div>
            <h2 className="text-base font-semibold text-ink">Add an agent</h2>
            <p className="text-[13px] text-ink-mute">
              Currency follows the agent&rsquo;s country automatically.
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
                invalid={Boolean(error)}
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

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={submitting}>
              Add agent
            </Button>
          </div>
        </form>
      </dialog>
    </>
  );
}
