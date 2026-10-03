import { NextResponse } from "next/server";

/**
 * Forwards a backend JSON response as-is: status and body, with the JSON content type. For the
 * pass-through proxies whose body carries nothing secret (a Login's deactivation timestamp, a
 * coded 409); {@link forwardSecretJson} is the one for a generated password.
 */
export async function forwardJson(backendResponse: Response): Promise<NextResponse> {
  const body = await backendResponse.text();
  return new NextResponse(body, {
    status: backendResponse.status,
    headers: { "Content-Type": "application/json" },
  });
}
