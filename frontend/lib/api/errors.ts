/**
 * The machine-readable `code` of an error response, when the backend sends one — e.g. an Agent
 * login 409's `USERNAME_TAKEN` or `AGENT_ALREADY_HAS_LOGIN`. `null` for any body without one.
 */
export async function readErrorCode(response: Response): Promise<string | null> {
  const body = (await response.json().catch(() => null)) as { code?: unknown } | null;
  return typeof body?.code === "string" ? body.code : null;
}
