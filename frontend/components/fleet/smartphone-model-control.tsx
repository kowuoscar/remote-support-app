"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CorrectionError } from "@/components/fleet/correction-error";
import { useInlineEdit } from "@/components/fleet/use-inline-edit";

const MODEL_MESSAGES: Record<string, string> = {
  MODEL_TOO_LONG: "A model can be at most 255 characters.",
};

/**
 * Corrects a Smartphone's model from the Fleet page (correct-a-fleet-unit spec): the Manager and
 * the Contract's Agent. Same inline edit-toggle shape as `SmartphoneSerialControl`, no dialog.
 * Trims before sending, refuses a blank value without a request, and keeps what was typed when
 * the backend refuses.
 */
export function SmartphoneModelControl({
  contractId,
  smartphoneId,
  model,
}: {
  contractId: string;
  smartphoneId: string;
  model: string;
}) {
  const [value, setValue] = useState(model);
  const { focusTarget, ...edit } = useInlineEdit({
    url: `/api/contracts/${contractId}/smartphones/${smartphoneId}/model`,
    messages: MODEL_MESSAGES,
    onStart: () => setValue(model),
  });

  if (!edit.editing) {
    return (
      <div className="flex items-center gap-2">
        <span className="whitespace-nowrap font-medium text-ink">{model}</span>
        <Button
          ref={focusTarget}
          variant="ghost"
          size="sm"
          aria-label={`Edit model, ${model}`}
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
          edit.setError("Enter a model.");
          return;
        }
        void edit.submit(event, { model: trimmed });
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
          aria-label="Smartphone model"
          aria-describedby={edit.error ? edit.errorId : undefined}
          className="h-7 w-36 text-label-sm"
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
