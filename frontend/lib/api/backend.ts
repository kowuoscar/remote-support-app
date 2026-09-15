import { cookies } from "next/headers";
import { SESSION_COOKIE_NAME } from "@/lib/auth/session";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

/**
 * Server-side fetch helper for calling the real backend with the caller's session token: reads
 * the httpOnly session cookie (the JWT itself — see `app/api/session/route.ts`) and forwards it
 * as a Bearer token. Used by Server Components that need real data (manager-entity-setup
 * ticket); mirrors the same BACKEND_URL/cookie pattern `app/api/session/route.ts` already
 * established for login, so there's exactly one way the frontend talks to the backend.
 */
export async function backendFetch(path: string, init?: RequestInit): Promise<Response> {
  const token = (await cookies()).get(SESSION_COOKIE_NAME)?.value;

  return fetch(`${BACKEND_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
    cache: "no-store",
  });
}

/** Calls the backend and parses a JSON array response, or returns `[]` on any non-OK response. */
export async function backendFetchList<T>(path: string): Promise<T[]> {
  const response = await backendFetch(path);
  if (!response.ok) return [];
  return (await response.json()) as T[];
}
