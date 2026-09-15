import type { ReactNode } from "react";
import { ShellFrame } from "@/components/app-shell/app-shell";

export default function ManagerLayout({ children }: { children: ReactNode }) {
  return (
    <ShellFrame surface="manager" roleLabel="Manager Console">
      {children}
    </ShellFrame>
  );
}
