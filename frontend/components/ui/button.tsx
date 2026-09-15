import { type ButtonHTMLAttributes, forwardRef } from "react";
import { cn } from "@/lib/cn";

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger" | "row";
type ButtonSize = "md" | "sm";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
}

/**
 * Primary commit actions (Send Invoice, Approve, Submit Request, …) are true
 * pills. Every other control — including dense row actions — uses the
 * compact 8px-radius (`rounded-lg`) shape reserved for non-primary controls.
 */
const variantClasses: Record<ButtonVariant, string> = {
  primary:
    "bg-primary text-on-primary rounded-full hover:bg-primary-hover active:bg-primary-press disabled:bg-primary/40",
  secondary:
    "bg-canvas text-ink border border-hairline-strong rounded-lg hover:bg-canvas-soft active:bg-canvas-soft disabled:opacity-50",
  ghost:
    "bg-transparent text-ink-secondary rounded-lg hover:bg-canvas-soft hover:text-ink active:bg-hairline disabled:opacity-50",
  danger:
    "bg-danger text-white rounded-lg hover:brightness-110 active:brightness-95 disabled:opacity-50",
  row: "bg-primary-soft-bg text-primary-soft-text rounded-lg hover:bg-primary/20 active:bg-primary/25 disabled:opacity-50",
};

const sizeClasses: Record<ButtonSize, string> = {
  md: "h-9 px-4 text-sm",
  sm: "h-7 px-3 text-[13px]",
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "secondary", size = "md", loading, className, children, disabled, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      className={cn(
        "inline-flex items-center justify-center gap-1.5 whitespace-nowrap font-medium transition-colors disabled:cursor-not-allowed",
        variantClasses[variant],
        sizeClasses[size],
        className,
      )}
      {...props}
    >
      {loading ? (
        <svg
          className="h-3.5 w-3.5 animate-spin"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
        >
          <circle
            cx="12"
            cy="12"
            r="9"
            stroke="currentColor"
            strokeWidth="2.5"
            opacity="0.25"
          />
          <path
            d="M21 12a9 9 0 0 0-9-9"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
          />
        </svg>
      ) : null}
      {children}
    </button>
  );
});
