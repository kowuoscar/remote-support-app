import { Card } from "@/components/ui/card";
import { LoginForm } from "@/components/login/login-form";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { IconCheckCircle } from "@/components/icons";

export const metadata = { title: "Sign in" };

/**
 * The one surface with no left rail — it runs before there's a session to scope a nav to. Same
 * header mark as the shell's nav rail (DESIGN.md Layout), centered form below: the established
 * world extended to a new page, not a new composition.
 *
 * `?passwordChanged=1` (change-password-dialog ticket) renders the confirmation the human's
 * session decision calls for: a successful change signs the user out of this browser and lands
 * them here, so they prove the new password immediately rather than taking the product's word for
 * it (spec.md `## Decisions taken`). Parsed once here (coding standards frontend rule 14) — no
 * other value ever needs it, so it isn't threaded any further than this one banner.
 */
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ passwordChanged?: string | string[] }>;
}) {
  const passwordChanged = (await searchParams).passwordChanged === "1";

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

      <main className="flex flex-1 items-center justify-center px-6 py-16">
        <div className="w-full max-w-sm">
          <div className="mb-6 text-center">
            <h1 className="text-2xl font-semibold text-ink">Sign in</h1>
            <p className="mt-1 text-[13px] text-ink-mute">
              Use your Manager, Agent or Client Portal credentials.
            </p>
          </div>
          {passwordChanged ? (
            <div
              role="status"
              className="mb-4 flex items-start gap-2 rounded-lg bg-success-bg px-3 py-2.5 text-[13px] text-success"
            >
              <IconCheckCircle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>Your password was changed. Sign in with your new password.</span>
            </div>
          ) : null}
          <Card className="p-6">
            <LoginForm />
          </Card>
        </div>
      </main>
    </div>
  );
}
