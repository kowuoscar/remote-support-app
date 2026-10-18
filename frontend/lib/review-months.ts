import type { ReviewClientInvoice, ReviewMonthSummary } from "@/lib/api/types";

const MONTH_PARAM = /^\d{4}-(0[1-9]|1[0-2])$/;

/**
 * The month the Manager's Agent view opens on (manager-reviews-by-agent spec, "The page"). `months`
 * is the Agent's sent months, newest first; `currentMonth` a first-of-month ISO date. A `?month=`
 * that parses as YYYY-MM wins, listed or not; else the oldest month with something waiting (it has
 * waited longest), else the newest listed, else the current month (an empty state follows).
 * Returns a first-of-month ISO date.
 */
export function reviewMonthFor(
  months: ReviewMonthSummary[],
  param: string | undefined,
  currentMonth: string,
): string {
  if (param !== undefined && MONTH_PARAM.test(param)) return `${param}-01`;
  const waiting = months.filter((month) => month.waiting > 0);
  const oldestWaiting = waiting[waiting.length - 1];
  if (oldestWaiting) return oldestWaiting.billingMonth;
  return months[0]?.billingMonth ?? currentMonth;
}

/**
 * The invoice "Next invoice →" opens (manager-reviews-by-agent spec, `nextToReview`): the first
 * TO_REVIEW row after the current one in the month list's order, wrapping round to the start and
 * never the current one. When the current invoice is not listed it is the first TO_REVIEW row; with
 * none, null. The current invoice is excluded whatever its own status, so a page that has just
 * approved or sent it back still points elsewhere.
 */
export function nextToReview(clientInvoices: ReviewClientInvoice[], currentId: string): ReviewClientInvoice | null {
  const currentIndex = clientInvoices.findIndex((invoice) => invoice.id === currentId);
  const ordered =
    currentIndex === -1
      ? clientInvoices
      : [...clientInvoices.slice(currentIndex + 1), ...clientInvoices.slice(0, currentIndex)];
  return ordered.find((invoice) => invoice.reviewStatus === "TO_REVIEW") ?? null;
}
