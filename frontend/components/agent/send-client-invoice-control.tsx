"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { IconAlertTriangle, IconArrowRight } from "@/components/icons";

/**
 * Agent sends their own draft Client Invoice (client-invoice-submission-and-visibility ticket AC:
 * "Agent can send a draft Client Invoice, moving it to status sent; a sent invoice is no longer
 * editable by the Agent"). A two-step inline confirm rather than a single click — mirrors
 * RequestStatusControl's cancel flow — because sending is a one-way door: there is no "un-send"
 * anywhere in this app, it opens the invoice to the Client and the Manager's review queue, and it
 * freezes the numbers permanently (see ClientInvoice's Javadoc on the backend).
 */
export function SendClientInvoiceControl({ contractId }: { contractId: string }) {
  const router = useRouter();
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function send() {
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/contracts/${contractId}/client-invoice/send`, { method: "POST" });
      if (!response.ok) {
        setError("Couldn't send. Try again.");
        setPending(false);
        return;
      }
      setConfirming(false);
      setPending(false);
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setPending(false);
    }
  }

  if (confirming) {
    return (
      <div className="flex flex-col items-end gap-1.5">
        <p className="max-w-[220px] text-right text-[12px] text-ink-mute">
          Sends to the Manager and Client, and locks the numbers. This can&rsquo;t be undone.
        </p>
        <div className="flex items-center gap-1.5">
          <Button type="button" variant="ghost" size="sm" disabled={pending} onClick={() => setConfirming(false)}>
            Back
          </Button>
          <Button type="button" variant="primary" size="sm" loading={pending} onClick={send}>
            Confirm send
          </Button>
        </div>
        {error ? (
          <p role="alert" className="flex items-center gap-1.5 text-[12px] text-danger">
            <IconAlertTriangle className="h-3.5 w-3.5 shrink-0" />
            {error}
          </p>
        ) : null}
      </div>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <Button type="button" variant="primary" size="sm" onClick={() => setConfirming(true)}>
        <IconArrowRight className="h-4 w-4" />
        Send Client Invoice
      </Button>
      {error ? (
        <p role="alert" className="flex items-center gap-1.5 text-[12px] text-danger">
          <IconAlertTriangle className="h-3.5 w-3.5 shrink-0" />
          {error}
        </p>
      ) : null}
    </div>
  );
}
