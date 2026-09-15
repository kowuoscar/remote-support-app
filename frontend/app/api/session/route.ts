import { NextRequest, NextResponse } from "next/server";
import { SESSION_COOKIE_NAME } from "@/lib/auth/session";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

// Matches the backend's default JWT expiration (application.yml
// app.jwt.expiration-minutes, default 60) so the cookie doesn't outlive the token it carries.
const SESSION_MAX_AGE_SECONDS = 60 * 60;

interface LoginRequestBody {
  username?: unknown;
  password?: unknown;
}

interface BackendLoginResponse {
  token: string;
  role: string;
}

/**
 * Backend-for-frontend proxy for login: the browser never sees the JWT. This route calls the
 * real `POST /api/auth/login`, then stores the token in an httpOnly cookie (never localStorage)
 * and returns only the role, which the client uses to route to the right shell.
 */
export async function POST(request: NextRequest) {
  let body: LoginRequestBody;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "Malformed request." }, { status: 400 });
  }

  const { username, password } = body;
  if (typeof username !== "string" || typeof password !== "string" || !username || !password) {
    return NextResponse.json({ error: "Email and password are required." }, { status: 400 });
  }

  let backendResponse: Response;
  try {
    backendResponse = await fetch(`${BACKEND_URL}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
      cache: "no-store",
    });
  } catch {
    return NextResponse.json({ error: "Could not reach the server. Try again." }, { status: 502 });
  }

  if (!backendResponse.ok) {
    return NextResponse.json({ error: "Incorrect email or password." }, { status: 401 });
  }

  const { token, role } = (await backendResponse.json()) as BackendLoginResponse;

  const response = NextResponse.json({ role });
  response.cookies.set({
    name: SESSION_COOKIE_NAME,
    value: token,
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: SESSION_MAX_AGE_SECONDS,
  });
  return response;
}

/** Logout: drop the session cookie client-side. The JWT itself expires server-side regardless. */
export async function DELETE() {
  const response = NextResponse.json({ ok: true });
  response.cookies.set({
    name: SESSION_COOKIE_NAME,
    value: "",
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: 0,
  });
  return response;
}
