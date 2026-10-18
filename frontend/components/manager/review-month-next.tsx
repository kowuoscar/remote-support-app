import Link from "next/link";
import { buttonClasses } from "@/components/ui/button";
import { IconArrowRight } from "@/components/icons";
import { formatBillingMonth } from "@/lib/format";
import { nextToReview } from "@/lib/review-months";
import type { ReviewMonth } from "@/lib/api/types";

/**
 * "Next invoice →" on a Manager's Client Invoice detail page (manager-reviews-by-agent spec,
 * `ReviewMonthNext`): a secondary link to the next invoice of the same Agent's month still To review,
 * or a quiet line once there is none. Renders nothing when the month could not be read, so a failed
 * read never gets in the way of Approve or Send back. Worked out at page load, not after an in-place
 * Approve or Send back (the current invoice is excluded anyway).
 */
export function ReviewMonthNext({ review, currentId }: { review: ReviewMonth | null; currentId: string }) {
  if (!review) return null;
  const next = nextToReview(review.clientInvoices, currentId);
  return (
    <div className="flex justify-end">
      {next ? (
        <Link
          href={`/manager/invoices/client/${next.id}`}
          aria-label={`Next invoice: ${next.clientName}`}
          className={buttonClasses({ variant: "secondary", size: "sm" })}
        >
          Next invoice
          <IconArrowRight className="h-3.5 w-3.5" />
        </Link>
      ) : (
        <p className="text-label text-ink-mute">Nothing else to review in {formatBillingMonth(review.billingMonth)}.</p>
      )}
    </div>
  );
}
