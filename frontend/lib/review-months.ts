import type { ReviewMonthSummary } from "@/lib/api/types";

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
