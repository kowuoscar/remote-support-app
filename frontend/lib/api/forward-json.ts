import { NextResponse } from "next/server";

/**
 * Forwards a backend JSON response as-is: status and body, with the JSON content type. For the
 * pass-through proxies whose body carries nothing secret (a Login's deactivation timestamp, a
 * coded 409); {@link forwardSecretJson} is the one for a generated password. An empty body (a
 * `204`) passes through as no body and no content type: the Fetch spec forbids attaching even an
 * empty string body to a 204/205/304 response.
 */
export async function forwardJson(backendResponse: Response): Promise<NextResponse> {
  const body = await backendResponse.text();
  return new NextResponse(body || null, {
    status: backendResponse.status,
    headers: body ? { "Content-Type": "application/json" } : undefined,
  });
}
