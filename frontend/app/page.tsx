import Link from "next/link";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { IconArrowRight } from "@/components/icons";

const surfaces = [
  {
    href: "/manager",
    label: "Manager Console",
    description: "Configure Clients, Agents and Contracts. Review and approve invoices.",
  },
  {
    href: "/agent",
    label: "Agent Console",
    description: "Log Requests and Fees, manage Fleet status, assemble monthly invoices.",
  },
  {
    href: "/client",
    label: "Client Portal",
    description: "Submit and track Requests, review your Fleet and Invoices.",
  },
];

export default function Home() {
  return (
    <div className="flex min-h-dvh flex-col bg-canvas">
      <header className="flex h-14 items-center justify-between border-b border-hairline px-6">
        <div className="flex items-center gap-2">
          <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary text-[13px] font-semibold text-on-primary">
            N
          </div>
          <span className="text-[13px] font-semibold text-ink">Remote Support Platform</span>
        </div>
        <ThemeToggle />
      </header>

      <main className="mx-auto flex w-full max-w-xl flex-1 flex-col justify-center px-6 py-16">
        <p className="text-[13px] font-medium text-ink-mute">Choose a workspace</p>
        <h1 className="mt-1 text-2xl font-semibold text-ink">
          Sign-in is not built yet — this build links straight to each role.
        </h1>
        <nav className="mt-8 divide-y divide-hairline rounded-xl border border-hairline">
          {surfaces.map((surface) => (
            <Link
              key={surface.href}
              href={surface.href}
              className="group flex items-center justify-between gap-4 px-5 py-4 transition-colors hover:bg-canvas-soft"
            >
              <div>
                <p className="text-sm font-medium text-ink">{surface.label}</p>
                <p className="mt-0.5 text-[13px] text-ink-mute">{surface.description}</p>
              </div>
              <IconArrowRight className="h-4 w-4 shrink-0 text-ink-faint transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
            </Link>
          ))}
        </nav>
      </main>
    </div>
  );
}
