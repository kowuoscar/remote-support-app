"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { PickerSelect } from "@/components/fleet/picker-select";
import { CorrectionError } from "@/components/fleet/correction-error";
import { useInlineEdit } from "@/components/fleet/use-inline-edit";
import { SMARTPHONE_OWNER_LABEL, type SmartphoneOwnerValue } from "@/lib/api/types";

const OWNER_MESSAGES: Record<string, string> = {
  SMARTPHONE_ON_OPEN_RETURN: "This smartphone is on an open Return. Change its owner once the Return is done.",
};

/**
 * Corrects a Smartphone's owner from the Fleet page (correct-a-fleet-unit spec): the Manager and
 * the Contract's Agent. The edit-toggle shape of `SmartphoneModelControl` around a two-option
 * select; a misclick on an immediate-apply select would change a Return's path, so Save commits.
 * Enter on the select saves, as it does in a text field.
 */
export function SmartphoneOwnerControl({
  contractId,
  smartphoneId,
  model,
  owner,
}: {
  contractId: string;
  smartphoneId: string;
  /** The phone's model; names which unit the Edit button is for. */
  model: string;
  owner: SmartphoneOwnerValue;
}) {
  const [value, setValue] = useState<SmartphoneOwnerValue>(owner);
  const { focusTarget, ...edit } = useInlineEdit({
    url: `/api/contracts/${contractId}/smartphones/${smartphoneId}/owner`,
    messages: OWNER_MESSAGES,
    onStart: () => setValue(owner),
  });

  if (!edit.editing) {
    return (
      <div className="flex items-center gap-2">
        <span className="whitespace-nowrap">{SMARTPHONE_OWNER_LABEL[owner]}</span>
        <Button
          ref={focusTarget}
          variant="ghost"
          size="sm"
          aria-label={`Edit owner of ${model}`}
          onClick={edit.start}
        >
          Edit
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={(event) => edit.submit(event, { owner: value })} onKeyDown={edit.onKeyDown} className="flex flex-col items-start gap-1">
      <div className="flex items-center gap-1.5">
        <PickerSelect
          id={`owner-${smartphoneId}`}
          ariaLabel="Owner"
          autoFocus
          value={value}
          onChange={(next) => setValue(next as SmartphoneOwnerValue)}
          disabled={edit.pending}
          size="sm"
          placeholder="Choose an owner"
        >
          <option value="CLIENT">{SMARTPHONE_OWNER_LABEL.CLIENT}</option>
          <option value="COMPANY">{SMARTPHONE_OWNER_LABEL.COMPANY}</option>
        </PickerSelect>
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
