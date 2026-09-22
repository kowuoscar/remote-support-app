/**
 * The machine-readable `code` of an error response, when the backend sends one — e.g. an Agent
 * login 409's `USERNAME_TAKEN` or `AGENT_ALREADY_HAS_LOGIN`. `null` for any body without one.
 */
export async function readErrorCode(response: Response): Promise<string | null> {
  const body = (await response.json().catch(() => null)) as { code?: unknown } | null;
  return typeof body?.code === "string" ? body.code : null;
}

/**
 * The inline error every create-login dialog shows: text to display, which field (if any) to
 * flag invalid, and an optional way out when retrying can't help (e.g. the Agent it targets is
 * gone). Held once so `create-tester-dialog.tsx`, `create-agent-dialog.tsx` and
 * `create-agent-login-dialog.tsx` share one shape instead of three copies (review finding F8).
 *
 * `field`'s union is widened to also cover `ChangePasswordDialog`'s three password fields
 * ("current"/"new"/"confirmNew") rather than that dialog declaring a fourth, parallel error type
 * beside this one (review finding F5) — a dialog with only one or two of these fields simply never
 * produces the values it doesn't have.
 */
export type SubmitError = {
  message: string;
  field: "email" | "password" | "current" | "new" | "confirmNew" | null;
  link?: { href: string; label: string };
};

/**
 * The wording every create-login dialog shows for a 409 carrying `USERNAME_TAKEN` — the human's
 * exact chosen wording (globally-unique-usernames spec.md "Decisions taken"), held once here so
 * it cannot drift between the three dialogs that show it (review finding F8).
 */
export const USERNAME_TAKEN_MESSAGE = "That email is already in use. Choose another one and try again.";

/** The `SubmitError` every dialog shows for a 409 carrying `USERNAME_TAKEN`. */
export function usernameTakenError(): SubmitError {
  return { message: USERNAME_TAKEN_MESSAGE, field: "email" };
}
