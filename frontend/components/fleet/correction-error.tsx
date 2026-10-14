/** The refusal message under a correction control's field (`aria-describedby` points here). */
export function CorrectionError({ id, message }: { id: string; message: string | null }) {
  if (!message) return null;
  return (
    <span id={id} role="alert" className="max-w-56 text-label-sm text-danger">
      {message}
    </span>
  );
}
