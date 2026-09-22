"use client";

import { useRef } from "react";
import { ViewerMenu } from "@/components/app-shell/viewer-menu";
import {
  ChangePasswordDialog,
  type ChangePasswordDialogHandle,
} from "@/components/app-shell/change-password-dialog";

/**
 * Pairs `ViewerMenu`'s "Change password" item with `ChangePasswordDialog` behind the ref that
 * connects them (change-password-dialog ticket). This pairing, not `TopBar` itself, is the
 * smallest client boundary the interaction needs: `TopBar` renders every console's shell,
 * including pages that have nothing to do with a password, so promoting the whole shared shell to
 * a Client Component would change hydration on every page it wraps for the sake of one menu item.
 * `ViewerMenu` was already `"use client"` (viewer-chip-menu ticket) and `ChangePasswordDialog`
 * must be one (its own form state, `DialogShell`'s native `showModal()`), so this wrapper adds no
 * client code beyond the ref itself — `TopBar` stays a Server Component (coding standards
 * frontend rule 1: add `"use client"` only when the file needs state, effects or browser APIs).
 */
export function ViewerActions({ viewerLabel }: { viewerLabel: string }) {
  const changePasswordRef = useRef<ChangePasswordDialogHandle>(null);

  return (
    <>
      <ViewerMenu viewerLabel={viewerLabel} onChangePassword={() => changePasswordRef.current?.open()} />
      <ChangePasswordDialog ref={changePasswordRef} />
    </>
  );
}
