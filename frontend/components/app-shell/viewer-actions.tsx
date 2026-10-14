"use client";

import { useRef } from "react";
import { useViewerCanChangeName } from "@/components/app-shell/viewer-capabilities";
import { ViewerMenu } from "@/components/app-shell/viewer-menu";
import { ChangeNameDialog, type ChangeNameDialogHandle } from "@/components/app-shell/change-name-dialog";
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
  // True under the Manager console only (real-manager-dashboard): adds "Change name" and its dialog.
  const viewerCanChangeName = useViewerCanChangeName();
  const changeNameRef = useRef<ChangeNameDialogHandle>(null);
  const changePasswordRef = useRef<ChangePasswordDialogHandle>(null);

  return (
    <>
      <ViewerMenu
        viewerLabel={viewerLabel}
        onChangeName={viewerCanChangeName ? () => changeNameRef.current?.open() : undefined}
        onChangePassword={() => changePasswordRef.current?.open()}
      />
      {viewerCanChangeName ? <ChangeNameDialog ref={changeNameRef} /> : null}
      <ChangePasswordDialog ref={changePasswordRef} />
    </>
  );
}
