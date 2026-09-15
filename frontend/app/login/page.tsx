import { Card } from "@/components/ui/card";
import { LoginForm } from "@/components/login/login-form";
import { ThemeToggle } from "@/components/theme/theme-toggle";

export const metadata = { title: "Sign in" };

/**
 * The one surface with no left rail — it runs before there's a session to scope a nav to. Same
 * header mark as the shell's nav rail (DESIGN.md Layout), centered form below: the established
 * world extended to a new page, not a new composition.
 */
export default function LoginPage() {
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
          <Card className="p-6">
            <LoginForm />
          </Card>
        </div>
      </main>
    </div>
  );
}
