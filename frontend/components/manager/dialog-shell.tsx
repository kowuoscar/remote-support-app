"use client";

import { forwardRef, useEffect, useImperativeHandle, useRef, type ReactNode } from "react";

export type DialogShellHandle = { open: () => void; close: () => void };

/**
 * The native `<dialog>` shell every Manager create/login dialog shares: open and close via a
 * ref handle, Escape blocked while `submitting`, and a click outside the form (on the dialog's
 * own padding — its backdrop) closes it the same way.
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

  useImperativeHandle(ref, () => ({
    open: () => dialogRef.current?.showModal(),
    close: () => dialogRef.current?.close(),
  }));

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    function handleBackdropClick(event: MouseEvent) {
      if (event.target === dialogRef.current && !submitting) dialogRef.current?.close();
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
      className={`m-auto ${widthClassName} rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]`}
    >
      {children}
    </dialog>
  );
});
