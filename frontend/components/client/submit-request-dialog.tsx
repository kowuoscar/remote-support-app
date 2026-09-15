"use client";

import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import type { ContractOption } from "@/components/ui/contract-switcher";
import type { RequestType } from "@/lib/demo/types";

const requestTypes: RequestType[] = [
  "Reboot",
  "Topup",
  "SIM Swap",
  "Provision Smartphone",
  "Provision SIM",
  "Repair",
];

export function SubmitRequestDialog({ contracts }: { contracts: ContractOption[] }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [submitted, setSubmitted] = useState(false);

  function open() {
    setSubmitted(false);
    dialogRef.current?.showModal();
  }

  function close() {
    dialogRef.current?.close();
  }

  return (
    <>
      <Button variant="primary" onClick={open}>
        Submit Request
      </Button>
      <dialog
        ref={dialogRef}
        onCancel={close}
        onClick={(event) => {
          if (event.target === dialogRef.current) close();
        }}
        className="m-auto w-[min(480px,90vw)] rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]"
      >
        {submitted ? (
          <div className="flex flex-col items-center gap-3 p-8 text-center">
            <div className="flex h-11 w-11 items-center justify-center rounded-full bg-success-bg text-success">
              <svg viewBox="0 0 24 24" fill="none" className="h-5 w-5" aria-hidden="true">
                <path
                  d="m6.5 12.5 3.5 3.5 7.5-8"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </div>
            <p className="text-sm font-medium text-ink">Request submitted</p>
            <p className="text-[13px] text-ink-mute">
              Your Agent will pick this up and update its status here as they work on it.
            </p>
            <Button variant="secondary" onClick={close}>
              Close
            </Button>
          </div>
        ) : (
          <form
            className="flex flex-col gap-4 p-6"
            onSubmit={(event) => {
              event.preventDefault();
              setSubmitted(true);
            }}
          >
            <div>
              <h2 className="text-base font-semibold text-ink">Submit a Request</h2>
              <p className="text-[13px] text-ink-mute">
                Your Agent is notified immediately and it queues for action.
              </p>
            </div>

            <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
              Contract
              <select
                required
                defaultValue={contracts[0]?.id}
                className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
              >
                {contracts.map((contract) => (
                  <option key={contract.id} value={contract.id}>
                    {contract.label}
                  </option>
                ))}
              </select>
            </label>

            <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
              Request type
              <select
                required
                defaultValue={requestTypes[0]}
                className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
              >
                {requestTypes.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </label>

            <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
              Note (optional)
              <textarea
                rows={3}
                placeholder="Anything your Agent should know before they start…"
                className="resize-none rounded-lg border border-hairline-strong bg-canvas px-3 py-2 text-sm text-ink placeholder:text-ink-mute focus-visible:border-primary"
              />
            </label>

            <div className="flex justify-end gap-2 pt-1">
              <Button type="button" variant="secondary" onClick={close}>
                Cancel
              </Button>
              <Button type="submit" variant="primary">
                Submit Request
              </Button>
            </div>
          </form>
        )}
      </dialog>
    </>
  );
}
