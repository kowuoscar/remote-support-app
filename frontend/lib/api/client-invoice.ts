import type { ClientInvoiceDetail } from "@/lib/api/types";

/**
 * Reads the by-id Client Invoice response for the Agent's own page. Another Agent's invoice (403),
 * an unknown id and another Tenant's id (404) all show the same not-found state — the page never
 * says which, so it reveals nothing about an invoice that is not the Agent's. Anything else wrong
 * is a real failure and throws.
 */
export async function clientInvoiceFromByIdResponse(response: Response): Promise<ClientInvoiceDetail | null> {
  if (response.status === 403 || response.status === 404) return null;
  if (!response.ok) throw new Error(`Couldn't load the Client Invoice (${response.status})`);
  return (await response.json()) as ClientInvoiceDetail;
}
