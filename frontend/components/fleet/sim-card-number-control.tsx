"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CorrectionError } from "@/components/fleet/correction-error";
import { useInlineEdit } from "@/components/fleet/use-inline-edit";

const NUMBER_MESSAGES: Record<string, string> = {
  NUMBER_TOO_LONG: "A SIM number can be at most 64 characters.",
};

/**
 * Corrects a SIM Card's number from the Fleet page (correct-a-fleet-unit spec): the Manager and
 * the Contract's Agent. Same inline edit-toggle shape as `SmartphoneSerialControl`, no dialog.
 * Trims before sending, refuses a blank value without a request, and keeps what was typed when
 * the backend refuses.
 */
export function SimCardNumberControl({
  contractId,
  simCardId,
  number,
}: {
  contractId: string;
  simCardId: string;
  number: string;
}) {
  const [value, setValue] = useState(number);
  const { focusTarget, ...edit } = useInlineEdit({
    url: `/api/contracts/${contractId}/sim-cards/${simCardId}/number`,
    messages: NUMBER_MESSAGES,
    onStart: () => setValue(number),
  });

  if (!edit.editing) {
    return (
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <span className="tnum whitespace-nowrap font-medium text-ink">{number}</span>
        <Button
          ref={focusTarget}
          variant="ghost"
          size="sm"
          aria-label={`Edit number, ${number}`}
          onClick={edit.start}
        >
          Edit
        </Button>
      </div>
    );
  }

  return (
    <form
      onSubmit={(event) => {
        const trimmed = value.trim();
        if (!trimmed) {
          event.preventDefault();
          edit.setError("Enter a number.");
          return;
        }
        void edit.submit(event, { number: trimmed });
      }}
      onKeyDown={edit.onKeyDown}
      className="flex flex-col items-start gap-1"
    >
      <div className="flex items-center gap-1.5">
        <Input
          autoFocus
          value={value}
          onChange={(event) => setValue(event.target.value)}
          disabled={edit.pending}
          invalid={edit.error !== null}
          aria-label="SIM number"
          aria-describedby={edit.error ? edit.errorId : undefined}
          inputMode="tel"
          className="tnum h-7 w-40 text-label-sm"
        />
        <Button type="submit" variant="row" size="sm" loading={edit.pending}>
          Save
        </Button>
        <Button type="button" variant="ghost" size="sm" disabled={edit.pending} onClick={edit.cancel}>
          Cancel
        </Button>
      </div>
      <CorrectionError id={edit.errorId} message={edit.error} />
    </form>
  );
}
