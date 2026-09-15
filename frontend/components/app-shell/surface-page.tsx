import type { ReactNode } from "react";
import { TopBar } from "@/components/app-shell/top-bar";
import { DemoNote } from "@/components/ui/demo-note";

export function SurfacePage({
  title,
  subtitle,
  actions,
  viewerLabel,
  children,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  viewerLabel: string;
  children: ReactNode;
}) {
  return (
    <>
      <TopBar title={title} subtitle={subtitle} actions={actions} viewerLabel={viewerLabel} />
      <main className="min-w-0 flex-1 px-4 py-5 sm:px-6 sm:py-6" data-testid="surface-main">
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-6">{children}</div>
      </main>
      <DemoNote />
    </>
  );
}
