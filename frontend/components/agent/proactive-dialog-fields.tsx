import { IconAlertTriangle } from "@/components/icons";
import type { ContractTesterListItem } from "@/lib/api/types";

/**
 * The inline error alert shared by the Agent's proactive "Log a request" and "Log a fee"
 * dialogs — extracted with no change to markup or classes.
 */
export function FormErrorAlert({ error }: { error: string | null }) {
  if (!error) return null;
  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-lg bg-danger-bg px-3 py-2.5 text-[13px] text-danger"
    >
      <IconAlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
      <span>{error}</span>
    </div>
  );
}

/**
 * The Tester `<select>` shared by LogRequestDialog and LogFeeDialog — both dialogs are scoped to
 * one Contract and name which of its Testers the Request/Fee is for. Extracted with no change to
 * markup or classes; each caller still renders its own "no Testers yet" fallback around it.
 */
export function TesterSelectField({
  testers,
  disabled,
}: {
  testers: ContractTesterListItem[];
  disabled: boolean;
}) {
  return (
    <label className="flex flex-col gap-1.5 text-[13px] font-medium text-ink-secondary">
      Tester
      <select
        name="testerId"
        required
        defaultValue={testers[0]?.id}
        disabled={disabled}
        className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
      >
        {testers.map((tester) => (
          <option key={tester.id} value={tester.id}>
            {tester.username}
          </option>
        ))}
      </select>
    </label>
  );
}
