import type { Page } from "@playwright/test";
import { SESSION_COOKIE_NAME } from "@/lib/auth/session";

/** Sets `session` as the session cookie (the stub backend knows it by token) and opens `path`. */
export async function gotoWithSession(page: Page, path: string, session: string) {
  await page.context().addCookies([{ name: SESSION_COOKIE_NAME, value: session, url: "http://127.0.0.1:4173" }]);
  await page.goto(path);
}
