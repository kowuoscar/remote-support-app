"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

/**
 * A single-confirm POST action against an invoice-by-id route: pending state, an inline error on
 * failure (the caller's own copy for a 409, a generic one otherwise), the parsed response handed
 * to the caller's `onSuccess` on success, then a refresh. Shared by ApproveClientInvoiceControl,
 * ApproveAgentInvoiceControl and MarkAgentInvoicePaidControl — same shape, different endpoint and
 * copy.
 */
export function usePostAction<T>(
  url: string,
  conflictMessage: string,
  genericMessage: string,
): { pending: boolean; error: string | null; run: (onSuccess?: (result: T) => void) => Promise<void> } {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(onSuccess?: (result: T) => void) {
    setPending(true);
    setError(null);
    try {
      const response = await fetch(url, { method: "POST" });
      if (!response.ok) {
        setError(response.status === 409 ? conflictMessage : genericMessage);
        setPending(false);
        return;
      }
      onSuccess?.((await response.json()) as T);
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setPending(false);
    }
  }

  return { pending, error, run };
}
