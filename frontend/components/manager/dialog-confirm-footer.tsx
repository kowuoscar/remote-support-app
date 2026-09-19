import { Button } from "@/components/ui/button";

/**
 * The Cancel/Confirm button row shared by every one-way confirmation dialog (e.g. archiving a
 * Carrier, a Topup Option or a Postpaid Plan) — extracted from the identical copies in
 * archive-carrier-dialog.tsx and archive-carrier-offer-dialog.tsx.
 */
export function DialogConfirmFooter({
  onCancel,
  onConfirm,
  submitting,
  cancelLabel = "Keep it",
  confirmLabel,
}: {
  onCancel: () => void;
  onConfirm: () => void;
  submitting: boolean;
  cancelLabel?: string;
  confirmLabel: string;
}) {
  return (
    <div className="flex justify-end gap-2 pt-1">
      <Button type="button" variant="secondary" onClick={onCancel} disabled={submitting}>
        {cancelLabel}
      </Button>
      <Button type="button" variant="danger" loading={submitting} onClick={onConfirm}>
        {confirmLabel}
      </Button>
    </div>
  );
}
