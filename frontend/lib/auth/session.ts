/**
 * The httpOnly cookie holding the backend JWT. Shared between the login/logout route handler
 * (`app/api/session/route.ts`) and `middleware.ts`, which only checks for its *presence* — the
 * JWT itself is opaque to the frontend; the backend is the real authority on whether a token is
 * valid, and this middleware exists to route an unauthenticated visitor to `/login`, not to
 * re-implement token verification.
 */
export const SESSION_COOKIE_NAME = "rs_session";
