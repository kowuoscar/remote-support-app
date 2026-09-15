import { NextResponse, type NextRequest } from "next/server";
import { SESSION_COOKIE_NAME } from "@/lib/auth/session";

/**
 * Gates the three authenticated surfaces: no session cookie, no access — redirect to `/login`.
 * This only checks the cookie's *presence*; the backend remains the real authority on whether
 * the token inside it is valid (every real data request will carry it as a bearer token and be
 * rejected there if it isn't). That split keeps this middleware fast (no crypto per request) and
 * keeps the frontend from needing to duplicate the backend's JWT-verification logic.
 */
export function proxy(request: NextRequest) {
  if (request.cookies.has(SESSION_COOKIE_NAME)) {
    return NextResponse.next();
  }

  const loginUrl = new URL("/login", request.url);
  loginUrl.searchParams.set("from", request.nextUrl.pathname);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ["/manager/:path*", "/agent/:path*", "/client/:path*"],
};
