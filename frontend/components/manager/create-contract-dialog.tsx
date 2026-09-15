"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { IconAlertTriangle, IconContracts, IconPlus } from "@/components/icons";
import type { AgentListItem, ClientListItem } from "@/lib/api/types";

/**
 * Manager creates a Contract linking exactly one Client and one Agent (manager-entity-setup
 * ticket). Both come from real records already on the tenant — this dialog never creates a
 * Client or Agent inline, keeping "add an entity" a single, unambiguous action per dialog. If
 * either list is empty, the dialog explains what's missing instead of showing an unusable form.
 */
export function CreateContractDialog({
  clients,
  agents,
}: {
  clients: ClientListItem[];
  agents: AgentListItem[];
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const [clientId, setClientId] = useState(clients[0]?.id ?? "");
  const [agentId, setAgentId] = useState(agents[0]?.id ?? "");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const canSubmit = clients.length > 0 && agents.length > 0;

  function open() {
    setClientId(clients[0]?.id ?? "");
    setAgentId(agents[0]?.id ?? "");
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
      const response = await fetch("/api/contracts", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ clientId, agentId }),
      });

      if (!response.ok) {
        setError("Couldn't create the contract. Try again.");
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
        Add contract
      </Button>
      <dialog
        ref={dialogRef}
        onCancel={close}
        onClick={(event) => {
          if (event.target === dialogRef.current) close();
        }}
        className="m-auto w-[min(440px,90vw)] rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
      >
        {canSubmit ? (
          <form className="flex flex-col gap-4 p-6" onSubmit={handleSubmit}>
            <div>
              <h2 className="text-base font-semibold text-ink">Add a contract</h2>
              <p className="text-[13px] text-ink-mute">
                Currency is copied from the agent&rsquo;s currency automatically.
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
              Client
              <select
                required
                value={clientId}
                onChange={(event) => setClientId(event.target.value)}
                disabled={submitting}
                className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-70"
              >
                {clients.map((client) => (
                  <option key={client.id} value={client.id}>
                    {client.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
              Agent
              <select
                required
                value={agentId}
                onChange={(event) => setAgentId(event.target.value)}
                disabled={submitting}
                className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-70"
              >
                {agents.map((agent) => (
                  <option key={agent.id} value={agent.id}>
                    {agent.name} · {agent.currency}
                  </option>
                ))}
              </select>
            </label>

            <div className="flex justify-end gap-2 pt-1">
              <Button type="button" variant="secondary" onClick={close} disabled={submitting}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" loading={submitting}>
                Add contract
              </Button>
            </div>
          </form>
        ) : (
          <div className="p-6">
            <EmptyState
              icon={<IconContracts className="h-5 w-5" />}
              title="Add a client and an agent first"
              description="A contract links one existing client to one existing agent — create both before pairing them here."
              action={
                <Button variant="secondary" onClick={close}>
                  Close
                </Button>
              }
            />
          </div>
        )}
      </dialog>
    </>
  );
}
