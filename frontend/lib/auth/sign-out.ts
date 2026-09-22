import type { useRouter } from "next/navigation";

/**
 * The sign-out shape every place that logs the viewer out shares: drop the session cookie through
 * the existing `DELETE /api/session` route, then leave for `target` and refresh the router cache
 * so no stale, still-authenticated data lingers behind (review finding F4 — `ViewerMenu`'s "Log
 * out" item and `ChangePasswordDialog`'s post-change redirect had each grown their own copy of
 * this, after the deleted standalone `LogoutButton` had held the only one).
 */
export async function signOutAndRedirect(router: ReturnType<typeof useRouter>, target: string): Promise<void> {
  try {
    await fetch("/api/session", { method: "DELETE" });
  } finally {
    router.push(target);
    router.refresh();
  }
}
