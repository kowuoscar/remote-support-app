"use client";

import { useEffect, useRef, type ReactNode } from "react";
import type { ClientInvoiceStatusValue } from "@/lib/api/types";

/**
 * The card's one-line status note. When the invoice's status changes under the user (the Agent
 * sent it and the page refreshed), the control they were on is gone, so the note takes focus
 * (tabIndex -1) and a screen reader reads the new status instead of focus falling to the page. The
 * first render never steals focus.
 */
export function ClientInvoiceStatusNote({
  status,
  className,
  children,
}: {
  status: ClientInvoiceStatusValue;
  className?: string;
  children: ReactNode;
}) {
  const note = useRef<HTMLParagraphElement>(null);
  const previous = useRef(status);

  useEffect(() => {
    if (previous.current !== status) {
      previous.current = status;
      note.current?.focus();
    }
  }, [status]);

  return (
    <p ref={note} tabIndex={-1} className={`${className ?? ""} outline-none`}>
      {children}
    </p>
  );
}
