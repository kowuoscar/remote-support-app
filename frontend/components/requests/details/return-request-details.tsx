"use client";

import { useId } from "react";
import type { RequestDetailsProps } from "./types";

/**
 * A Return Request's own details (returns-and-agent-stock spec, Solution's Return type;
 * return-client-owned-smartphones ticket AC: "The submit and log-Request dialogs show a Return
 * details section with a multi-select of the Contract's Active units"). Registered against
 * `RETURN` in `registry.tsx`.
 *
 * <p>One `<select multiple>`, not two — "a multi-select of the Contract's Active units" (spec.md
 * Frontend) reads as a single control — grouped into Smartphones/SIM Cards `<optgroup>`s. Each
 * option's value is prefixed with which list it belongs to (`SMARTPHONE:<id>`/`SIM_CARD:<id>`)
 * since the backend takes two separate id lists (`returnedSmartphoneIds`/`returnedSimCardIds`);
 * `SubmitRequestDialog`/`LogRequestDialog` split the selected values back into the two on submit,
 * the same "backend field names as a plain FormData `name`" convention every sibling details
 * component already follows, just needing this one extra parsing step since a `<select multiple>`
 * has no `name`-per-kind of its own.
 */
export function ReturnRequestDetails({ smartphones, simCards, disabled }: RequestDetailsProps) {
  const id = useId();
  const activeSmartphones = smartphones.filter((phone) => phone.status === "ACTIVE");
  const activeSimCards = simCards.filter((simCard) => simCard.status === "ACTIVE");
  const noUnits = activeSmartphones.length === 0 && activeSimCards.length === 0;
  const optionCount = activeSmartphones.length + activeSimCards.length;

  return (
    <>
      <label htmlFor={id} className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        Units to return
        {noUnits ? (
          <p className="font-normal text-ink-mute">No Active units on this Contract yet.</p>
        ) : (
          <select
            id={id}
            name="returnedUnitIds"
            multiple
            required
            disabled={disabled}
            size={Math.min(6, Math.max(3, optionCount))}
            className="rounded-lg border border-hairline-strong bg-canvas px-3 py-2 text-sm text-ink focus-visible:border-primary"
          >
            {activeSmartphones.length > 0 ? (
              <optgroup label="Smartphones">
                {activeSmartphones.map((phone) => (
                  <option key={phone.id} value={`SMARTPHONE:${phone.id}`}>
                    {phone.model}
                    {phone.serial ? ` — ${phone.serial}` : ""}
                  </option>
                ))}
              </optgroup>
            ) : null}
            {activeSimCards.length > 0 ? (
              <optgroup label="SIM Cards">
                {activeSimCards.map((simCard) => (
                  <option key={simCard.id} value={`SIM_CARD:${simCard.id}`}>
                    {simCard.number}
                  </option>
                ))}
              </optgroup>
            ) : null}
          </select>
        )}
      </label>
      <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        Description <span className="font-normal text-ink-mute">(optional)</span>
        <textarea
          name="description"
          rows={2}
          disabled={disabled}
          placeholder="Anything the Agent should know"
          className="rounded-lg border border-hairline-strong bg-canvas px-3 py-2 text-sm text-ink focus-visible:border-primary"
        />
      </label>
    </>
  );
}

/**
 * Splits a `returnedUnitIds` multi-select's selected values back into the two id lists the
 * backend takes — shared by `SubmitRequestDialog` and `LogRequestDialog` so the `SMARTPHONE:`/
 * `SIM_CARD:` prefix convention above lives in exactly one place.
 */
export function parseReturnedUnitIds(values: string[]): {
  returnedSmartphoneIds: string[];
  returnedSimCardIds: string[];
} {
  const returnedSmartphoneIds: string[] = [];
  const returnedSimCardIds: string[] = [];
  for (const value of values) {
    if (value.startsWith("SMARTPHONE:")) {
      returnedSmartphoneIds.push(value.slice("SMARTPHONE:".length));
    } else if (value.startsWith("SIM_CARD:")) {
      returnedSimCardIds.push(value.slice("SIM_CARD:".length));
    }
  }
  return { returnedSmartphoneIds, returnedSimCardIds };
}
