"use client";

import { forwardRef } from "react";
import {
  LoginActivationDialog,
  type LoginActivationDialogHandle,
  type LoginActivationDialogProps,
} from "@/components/manager/login-activation-dialog";

export type { LoginActivationDialogHandle, LoginActivationTarget } from "@/components/manager/login-activation-dialog";

/** The confirm step for reactivating a Login (deactivate-a-login spec); see LoginActivationDialog. */
export const ReactivateLoginDialog = forwardRef<LoginActivationDialogHandle, LoginActivationDialogProps>(
  function ReactivateLoginDialog(props, ref) {
    return <LoginActivationDialog ref={ref} mode="reactivate" {...props} />;
  },
);
