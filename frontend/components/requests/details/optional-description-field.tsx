/**
 * The optional free-text "Description" field shared by every per-type Request-details component
 * that has nothing more specific to ask for it (Reboot, Provision Smartphone/SIM, Replace
 * Smartphone/SIM, SIM Swap, and Topup's own "has an Option" branch) — extracted with no change to
 * markup or classes. Topup's own "no active Option" branch keeps its required, differently-worded
 * variant inline, since it isn't this one.
 */
export function OptionalDescriptionField({ disabled, rows = 2 }: { disabled?: boolean; rows?: number }) {
  return (
    <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
      Description <span className="font-normal text-ink-mute">(optional)</span>
      <textarea
        name="description"
        rows={rows}
        disabled={disabled}
        placeholder="Anything the Agent should know"
        className="rounded-lg border border-hairline-strong bg-canvas px-3 py-2 text-sm text-ink focus-visible:border-primary"
      />
    </label>
  );
}
