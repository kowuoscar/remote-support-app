"use client";

import { createContext, useContext, type ReactNode } from "react";

const ViewerCanChangeNameContext = createContext(false);

/**
 * Set once by `ShellFrame` for the Manager console (real-manager-dashboard): every page under it
 * then offers "Change name" in the account menu, without each page passing a flag down through
 * `SurfacePage` and `TopBar`.
 */
export function ViewerCanChangeNameProvider({ value, children }: { value: boolean; children: ReactNode }) {
  return <ViewerCanChangeNameContext.Provider value={value}>{children}</ViewerCanChangeNameContext.Provider>;
}

export function useViewerCanChangeName(): boolean {
  return useContext(ViewerCanChangeNameContext);
}
