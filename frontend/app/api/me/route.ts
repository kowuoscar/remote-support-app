import { backendFetch } from "@/lib/api/backend";
import { forwardJson } from "@/lib/api/forward-json";

/**
 * BFF proxy for `GET /api/me` (manager-changes-own-name ticket), so the browser-side
 * `ChangeNameDialog` can read the caller's current Manager name without the JWT leaving the
 * httpOnly cookie. A plain pass-through of status and body.
 */
export async function GET() {
  return forwardJson(await backendFetch("/api/me"));
}
