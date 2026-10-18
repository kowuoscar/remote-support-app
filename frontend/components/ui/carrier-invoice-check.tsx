import { IconCheckCircle } from "@/components/icons";

/**
 * The quiet confirmation after a Postpaid SIM line's "{Carrier} · Postpaid" label that its Carrier
 * Invoice File is attached. Non-interactive; shared by the Agent's card and the Manager's detail
 * so their two copies of the line layout do not drift. Renders nothing for a line without a file.
 */
export function CarrierInvoiceCheck({ carrierInvoiceFileId }: { carrierInvoiceFileId?: string | null }) {
  if (!carrierInvoiceFileId) return null;
  return (
    <span
      role="img"
      aria-label="Carrier invoice attached"
      className="ml-1.5 inline-block align-middle text-success"
    >
      <IconCheckCircle className="h-3.5 w-3.5" aria-hidden="true" />
    </span>
  );
}
