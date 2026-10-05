"use client";

import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState, type ReactNode } from "react";

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
    /** Called once each time the dialog closes, however it closed (handle, Escape, backdrop). */
    onClosed?: () => void;
    children: ReactNode;
  }
>(function DialogShell({ submitting, widthClassName = "w-[min(440px,90vw)]", titleId, onClosed, children }, ref) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [mounted, setMounted] = useState(false);
  const isOpenRef = useRef(false);
  const onClosedRef = useRef(onClosed);
  useEffect(() => {
    onClosedRef.current = onClosed;
  });

  const dismiss = useCallback(() => {
    setMounted(false);
    if (!isOpenRef.current) return;
    isOpenRef.current = false;
    onClosedRef.current?.();
  }, []);

  // Closes now, in the event that asked for it. The native `close` event arrives later, in its own
  // task: closing only from that event left `mounted` true through a quick Escape-then-Enter, so
  // the re-open's setMounted(true) changed nothing, showModal never ran, and the late `close`
  // event then unmounted the dialog the user had just asked for.
  const closeNow = useCallback(() => {
    dialogRef.current?.close();
    dismiss();
  }, [dismiss]);

  useImperativeHandle(ref, () => ({
    open: () => {
      isOpenRef.current = true;
      setMounted(true);
    },
    close: closeNow,
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
      closeNow();
    }
    dialog.addEventListener("click", handleBackdropClick);
    return () => dialog.removeEventListener("click", handleBackdropClick);
  }, [submitting, closeNow]);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onCancel={(event) => {
        // Escape: never let the browser close it on its own schedule (see closeNow).
        event.preventDefault();
        if (!submitting) closeNow();
      }}
      // A `close` that arrives after a re-open belongs to the earlier session; ignore it.
      onClose={() => {
        if (!dialogRef.current?.open) dismiss();
      }}
      className={`m-auto ${widthClassName} rounded-xl border border-hairline bg-canvas-overlay p-0 shadow-elevated-strong backdrop:bg-ink/40 backdrop:backdrop-blur-[2px]`}
    >
      {mounted ? children : null}
    </dialog>
  );
});
