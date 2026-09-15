import type { ReactNode } from "react";
import { ShellFrame } from "@/components/app-shell/app-shell";

export default function ClientLayout({ children }: { children: ReactNode }) {
  return (
    <ShellFrame surface="client" roleLabel="Client Portal">
      {children}
    </ShellFrame>
  );
}
