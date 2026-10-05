import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { formatDate } from "@/lib/format";

/**
 * The neutral "Deactivated" tag beside a Login's email (deactivate-a-login spec). The date lives in
 * a tooltip and, for assistive tech, in screen-reader text; the visible badge is hidden from it so
 * the date is read once.
 */
export function DeactivatedTag({ deactivatedAt }: Readonly<{ deactivatedAt: string }>) {
  const since = `Deactivated since ${formatDate(deactivatedAt, { locale: "en-GB", timeZone: "UTC" })}`;
  return (
    <span title={since} className="whitespace-nowrap">
      <span aria-hidden="true">
        <Badge>Deactivated</Badge>
      </span>
      <span className="sr-only">{since}</span>
    </span>
  );
}

/**
 * One button whose label follows the Login's state, so it keeps its place in the row (and its
 * focus) across the refresh that follows a change. `person` names whose Login it is when the page
 * lists several (a Tester's row), so each button is told apart.
 */
export function LoginActivationButton({
  deactivated,
  person,
  onClick,
}: Readonly<{ deactivated: boolean; person?: string; onClick: () => void }>) {
  return (
    <Button
      variant="row"
      size="sm"
      aria-label={person ? `${deactivated ? "Reactivate" : "Deactivate"} login for ${person}` : undefined}
      onClick={onClick}
    >
      {deactivated ? "Reactivate login" : "Deactivate login"}
    </Button>
  );
}
