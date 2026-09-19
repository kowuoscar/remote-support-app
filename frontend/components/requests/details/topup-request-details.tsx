"use client";

import { useId, useState } from "react";
import { SimCardPicker } from "@/components/requests/sim-card-picker";
import { Money } from "@/components/ui/money";
import { OptionalDescriptionField } from "./optional-description-field";
import type { RequestDetailsProps } from "./types";

/**
 * A Topup Request's own details (request-types-and-flow spec's table: "Topup — Required: target
 * SIM Card; a Topup Option of that SIM Card's Carrier when the Carrier has an active one,
 * otherwise a description"; reboot-and-topup-details ticket). Registered against `TOPUP` in
 * `registry.tsx`. The Option picker follows the chosen SIM Card's own Carrier — pick a different
 * SIM Card and the list of Options (or the fallback description field) updates with it, exactly
 * the backend's own per-Carrier rule.
 */
export function TopupRequestDetails({ simCards, carriers, currency, disabled }: RequestDetailsProps) {
  const descriptionId = useId();
  const [simCardId, setSimCardId] = useState("");
  const [topupOptionId, setTopupOptionId] = useState("");
  const simCard = simCards.find((sim) => sim.id === simCardId);
  const carrier = simCard?.carrierId ? carriers.find((c) => c.id === simCard.carrierId) : undefined;
  const activeOptions = (carrier?.topupOptions ?? []).filter((option) => option.archivedAt === null);
  const needsDescription = simCard !== undefined && activeOptions.length === 0;
  const chosenOption = activeOptions.find((option) => option.id === topupOptionId);

  return (
    <>
      <SimCardPicker
        simCards={simCards}
        label="SIM Card to top up"
        name="targetSimCardId"
        value={simCardId}
        onChange={setSimCardId}
        disabled={disabled}
      />

      {needsDescription ? (
        <label
          htmlFor={descriptionId}
          className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary"
        >
          Description
          <textarea
            id={descriptionId}
            name="description"
            required
            rows={3}
            disabled={disabled}
            placeholder="This SIM Card's Carrier has no Topup Options — describe the top-up instead"
            className="rounded-lg border border-hairline-strong bg-canvas px-3 py-2 text-sm text-ink focus-visible:border-primary"
          />
        </label>
      ) : (
        <>
          <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
            Topup Option
            <select
              name="topupOptionId"
              required
              disabled={disabled || activeOptions.length === 0}
              value={topupOptionId}
              onChange={(event) => setTopupOptionId(event.target.value)}
              className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-70"
            >
              <option value="" disabled>
                {simCard ? "Choose a Topup Option" : "Choose a SIM Card first"}
              </option>
              {activeOptions.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.name}
                </option>
              ))}
            </select>
            {chosenOption && currency ? (
              <span className="flex items-baseline justify-between text-[12px] font-normal text-ink-mute">
                Suggested Fee amount
                <Money amount={chosenOption.price} currency={currency} />
              </span>
            ) : null}
          </label>
          <OptionalDescriptionField disabled={disabled} />
        </>
      )}
    </>
  );
}
