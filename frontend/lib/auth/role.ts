/**
 * Mirrors the backend's `Role` enum (backend/.../domain/Role.java). SUPER_ADMIN has no dedicated
 * shell this iteration (spec.md Non-goals) — a login as that role has nowhere to land, so it
 * isn't included in the landing map below.
 */
export type Role = "MANAGER" | "AGENT" | "TESTER" | "SUPER_ADMIN";

const ROLE_LANDING_PATH: Partial<Record<Role, string>> = {
  MANAGER: "/manager",
  AGENT: "/agent",
  TESTER: "/client",
};

/** Where a freshly logged-in user of this role lands. Falls back to `/login` for a role with no shell yet. */
export function landingPathForRole(role: Role): string {
  return ROLE_LANDING_PATH[role] ?? "/login";
}
