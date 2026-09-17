import { redirect } from "next/navigation";
import { backendFetch } from "@/lib/api/backend";

/**
 * Guards a Manager-only Server Component page (manager-entity-setup ticket: "Only a Manager can
 * create Clients, Testers, Agents and Contracts; Agent and Tester attempts are rejected").
 * `middleware.ts`/`proxy.ts` only checks that a session cookie is *present*, not its role — any
 * authenticated Client/Agent/Tester session can otherwise reach `/manager/**` in the browser. The
 * backend is the real authority on role, so this asks it via `/api/me` and sends a non-Manager
 * (or unauthenticated/expired-token) visitor back to `/login` rather than rendering a page they
 * have no data for. Scoped to the pages that call it (every backend-driven Manager page) rather
 * than the shared `ManagerLayout`, so the still-demo-data dashboard keeps working in the
 * visual-regression suite, which runs with no backend at all.
 */
export async function requireManager(): Promise<void> {
  const response = await backendFetch("/api/me");
  if (!response.ok) {
    redirect("/login");
  }

  const me = (await response.json()) as { role?: string };
  if (me.role !== "MANAGER") {
    redirect("/login");
  }
}
