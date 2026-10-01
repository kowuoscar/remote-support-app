"use client";

import { forwardRef, useEffect, useImperativeHandle, useRef, useState, type ReactNode } from "react";

export type DialogShellHandle = { open: () => void; close: () => void };

/**
 * The native `<dialog>` shell every Manager create/login dialog shares: open and close via a
 * ref handle, Escape blocked while `submitting`, and a click outside the form (on the dialog's
 * own padding — its backdrop) closes it the same way.
 *
 * Children are mounted only while the dialog is open, so a closed dialog leaves nothing (a
 * form, a shown password) in the page's DOM.
 *
 * The backdrop-click listener is attached imperatively with `addEventListener`, next to the
 * `showModal`/`close` calls it belongs with, instead of as a JSX `onClick` — `<dialog>` has no
 * interactive ARIA role, and a JSX mouse handler with no paired keyboard handler reads as a
 * reliability bug to static analysis. Escape already closes the dialog through the native
 * `cancel` event below, so there's no missing keyboard path to add.
 */
export const DialogShell = forwardRef<
  DialogShellHandle,
  {
    submitting: boolean;
    widthClassName?: string;
    titleId?: string;
    children: ReactNode;
  }
>(function DialogShell({ submitting, widthClassName = "w-[min(440px,90vw)]", titleId, children }, ref) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [mounted, setMounted] = useState(false);

  useImperativeHandle(ref, () => ({
    open: () => setMounted(true),
    close: () => {
      dialogRef.current?.close();
      setMounted(false);
    },
  }));

  // showModal() runs after the children mount, so the native focus step finds them.
  useEffect(() => {
    if (mounted && dialogRef.current && !dialogRef.current.open) dialogRef.current.showModal();
  }, [mounted]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    function handleBackdropClick(event: MouseEvent) {
      if (event.target !== dialogRef.current || submitting) return;
      dialogRef.current?.close();
      setMounted(false);
    }
    dialog.addEventListener("click", handleBackdropClick);
    return () => dialog.removeEventListener("click", handleBackdropClick);
  }, [submitting]);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onCancel={(event) => {
        if (submitting) event.preventDefault();
      }}
      onClose={() => setMounted(false)}
      className={`m-auto ${widthClassName} rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]`}
    >
      {mounted ? children : null}
    </dialog>
  );
});
