import { type InputHTMLAttributes, forwardRef } from "react";
import { cn } from "@/lib/cn";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
}

/**
 * The one generic text field for the system (DESIGN.md Inputs/Fields): 8px radius,
 * `hairline-strong` border, `canvas` background, 36px height, border shifts to `primary` on
 * `:focus-visible` — the global 2px outline ring (app/globals.css) still applies on top, same as
 * every other focusable control. `SearchInput` is the icon-prefixed sibling of this for the one
 * screen that needs it; this is the plain field for forms.
 */
export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { className, invalid, ...props },
  ref,
) {
  return (
    <input
      ref={ref}
      aria-invalid={invalid || undefined}
      className={cn(
        "h-9 w-full rounded-lg border bg-canvas px-3 text-sm text-ink placeholder:text-ink-faint transition-colors",
        "focus-visible:border-primary",
        "disabled:cursor-not-allowed disabled:bg-canvas-soft disabled:text-ink-mute disabled:opacity-70",
        invalid ? "border-danger" : "border-hairline-strong",
        className,
      )}
      {...props}
    />
  );
});
