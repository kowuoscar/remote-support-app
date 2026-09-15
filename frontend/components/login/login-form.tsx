"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { IconAlertTriangle } from "@/components/icons";
import type { Role } from "@/lib/auth/role";
import { landingPathForRole } from "@/lib/auth/role";

interface SessionSuccessBody {
  role: Role;
}

interface SessionErrorBody {
  error: string;
}

export function LoginForm() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const response = await fetch("/api/session", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });

      if (!response.ok) {
        const body = (await response.json().catch(() => null)) as SessionErrorBody | null;
        setError(body?.error ?? "Incorrect email or password.");
        setSubmitting(false);
        return;
      }

      const { role } = (await response.json()) as SessionSuccessBody;
      router.push(landingPathForRole(role));
      router.refresh();
    } catch {
      setError("Couldn't reach the server. Check your connection and try again.");
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      {error ? (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-lg bg-danger-bg px-3 py-2.5 text-[13px] text-danger"
        >
          <IconAlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      ) : null}

      <div>
        <label htmlFor="username" className="mb-1.5 block text-[13px] font-medium text-ink-secondary">
          Email
        </label>
        <Input
          id="username"
          name="username"
          type="email"
          inputMode="email"
          autoComplete="username"
          autoFocus
          required
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          disabled={submitting}
          invalid={Boolean(error)}
          placeholder="you@example.com"
        />
      </div>

      <div>
        <label htmlFor="password" className="mb-1.5 block text-[13px] font-medium text-ink-secondary">
          Password
        </label>
        <Input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          disabled={submitting}
          invalid={Boolean(error)}
          placeholder="••••••••"
        />
      </div>

      <Button type="submit" variant="primary" loading={submitting} className="mt-1 w-full">
        Sign in
      </Button>
    </form>
  );
}
