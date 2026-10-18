"use client";

import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { readErrorCode } from "@/lib/api/errors";
import { refreshPage } from "@/lib/selection-address";
import { IconAlertTriangle } from "@/components/icons";

/**
 * What a refused send says, by the `409` code the server answered with: the one mapping behind the
 * card's per-invoice send and the month bar's send (anything else reads as {@link SEND_FAILED}).
 */
const SEND_REFUSALS: Record<string, string> = {
  CARRIER_INVOICES_MISSING: "A Postpaid SIM still needs its carrier invoice. Reload to see which.",
  MONTH_NOT_SENT: "Send this month from the bar above. Reload to see it.",
  MONTH_CHANGED: "Your Contracts changed since this page loaded. Reload and try again.",
  MONTH_ALREADY_SENT: "This month was already sent. Reload to see it.",
  NOT_THE_CURRENT_MONTH: "The month has changed. Reload to see this month's invoices.",
};
const SEND_FAILED = "Couldn't send. Try again.";
const SEND_UNREACHABLE = "Couldn't reach the server. Check your connection and try again.";

/**
 * The two-step inline confirm both Client Invoice sends share (the one-way door for one invoice, or
 * for the whole month): `open` / `close`, Confirm taking focus when the confirm opens and the
 * trigger getting it back on Back or Escape, a pending Confirm that stays focusable (the caller sets
 * aria-disabled + aria-busy from `pending`; repeat activation is ignored), and the coded refusal line.
 * `copy` words the refusals of another one-way action sharing the shape (the Manager's month approval);
 * it defaults to the send's. After a successful send `pending` stays true ("Sending…") until the refreshed page replaces the control.
 */
export function useTwoStepConfirm(
  copy: { refusals: Record<string, string>; failed: string } = { refusals: SEND_REFUSALS, failed: SEND_FAILED },
) {
  const router = useRouter();
  const trigger = useRef<HTMLButtonElement>(null);
  const confirmButton = useRef<HTMLButtonElement>(null);
  const refocusTrigger = useRef(false);
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (confirming) {
      confirmButton.current?.focus();
    } else if (refocusTrigger.current) {
      refocusTrigger.current = false;
      trigger.current?.focus();
    }
  }, [confirming]);

  function close() {
    refocusTrigger.current = true;
    setConfirming(false);
    setError(null);
  }

  async function submit(request: () => Promise<Response>) {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      const response = await request();
      if (!response.ok) {
        const code = response.status === 409 ? await readErrorCode(response) : null;
        setError((code && copy.refusals[code]) || copy.failed);
        setPending(false);
        return;
      }
      refreshPage(router);
    } catch {
      setError(SEND_UNREACHABLE);
      setPending(false);
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key === "Escape" && !pending) close();
  }

  return {
    trigger,
    confirmButton,
    confirming,
    pending,
    error,
    open: () => setConfirming(true),
    close,
    submit,
    onKeyDown,
  };
}

/** The refusal line under a send's buttons. */
export function SendErrorLine({ error }: { error: string | null }) {
  if (!error) return null;
  return (
    <p role="alert" className="flex items-center gap-1.5 text-label-sm text-danger">
      <IconAlertTriangle className="h-3.5 w-3.5 shrink-0" />
      {error}
    </p>
  );
}
