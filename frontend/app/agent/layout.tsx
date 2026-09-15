import type { ReactNode } from "react";
import { ShellFrame } from "@/components/app-shell/app-shell";

export default function AgentLayout({ children }: { children: ReactNode }) {
  return (
    <ShellFrame surface="agent" roleLabel="Agent Console">
      {children}
    </ShellFrame>
  );
}
