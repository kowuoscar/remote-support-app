"use client";

import { useState } from "react";
import { SimCardPicker } from "@/components/requests/sim-card-picker";
import { SmartphonePicker } from "@/components/requests/smartphone-picker";
import type { RequestDetailsProps } from "./types";

type SwapMode = "MOVE" | "EXCHANGE";

/**
 * A SIM Swap Request's own details (request-types-and-flow spec's table: "SIM Swap — one move
 * (SIM Card → Smartphone), or an exchange: two SIM Cards installed in two different Smartphones";
 * sim-swap-moves ticket). Registered against `SIM_SWAP` in `registry.tsx`.
 *
 * <p>A move names any Active SIM Card and its destination Smartphone directly. An exchange only
 * offers SIM Cards that are currently Installed in a Smartphone (ticket's Component seam), and its
 * second picker only offers ones installed in a *different* Smartphone from the first — the
 * backend derives each SIM Card's destination from where the other one currently sits, so the form
 * never asks for a destination Smartphone in this mode.
 */
export function SimSwapRequestDetails({ smartphones, simCards, disabled }: RequestDetailsProps) {
  const [mode, setMode] = useState<SwapMode>("MOVE");
  const [firstSimCardId, setFirstSimCardId] = useState("");

  const installed = simCards.filter((sim) => sim.installedInSmartphoneId);
  const firstSimCard = installed.find((sim) => sim.id === firstSimCardId);
  const secondCandidates = installed.filter(
    (sim) =>
      sim.id !== firstSimCardId && sim.installedInSmartphoneId !== firstSimCard?.installedInSmartphoneId,
  );

  return (
    <>
      <fieldset className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
        <legend className="mb-0.5">What kind of swap?</legend>
        <label className="flex items-center gap-2 font-normal text-ink">
          <input
            type="radio"
            name="simSwapMode"
            value="MOVE"
            checked={mode === "MOVE"}
            onChange={() => setMode("MOVE")}
            disabled={disabled}
            className="h-4 w-4 accent-primary"
          />
          Move one SIM Card into another Smartphone
        </label>
        <label className="flex items-center gap-2 font-normal text-ink">
          <input
            type="radio"
            name="simSwapMode"
            value="EXCHANGE"
            checked={mode === "EXCHANGE"}
            onChange={() => setMode("EXCHANGE")}
            disabled={disabled}
            className="h-4 w-4 accent-primary"
          />
          Exchange the SIM Cards of two Smartphones
        </label>
      </fieldset>

      {mode === "MOVE" ? (
        <>
          <SimCardPicker
            simCards={simCards}
            label="SIM Card to move"
            name="targetSimCardId"
            disabled={disabled}
          />
          <SmartphonePicker
            smartphones={smartphones}
            label="Destination Smartphone"
            name="targetSmartphoneId"
            disabled={disabled}
          />
        </>
      ) : (
        <>
          <SimCardPicker
            simCards={installed}
            label="First SIM Card"
            name="targetSimCardId"
            value={firstSimCardId}
            onChange={setFirstSimCardId}
            disabled={disabled}
          />
          <SimCardPicker
            key={firstSimCardId}
            simCards={secondCandidates}
            label="Second SIM Card"
            name="secondSimCardId"
            disabled={disabled || !firstSimCard}
          />
        </>
      )}

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
