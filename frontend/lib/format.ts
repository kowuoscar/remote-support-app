const currencyFormatters = new Map<string, Intl.NumberFormat>();

export function formatMoney(amount: number, currency: string): string {
  let formatter = currencyFormatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat("en-US", {
      style: "currency",
      currency,
      currencyDisplay: "narrowSymbol",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
    currencyFormatters.set(currency, formatter);
  }
  return formatter.format(amount);
}

export function formatCompactMoney(amount: number, currency: string): string {
  const formatter = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    currencyDisplay: "narrowSymbol",
    notation: amount >= 100_000 ? "compact" : "standard",
    maximumFractionDigits: amount >= 100_000 ? 1 : 0,
  });
  return formatter.format(amount);
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

const monthNames = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

/**
 * Turns a "Month YYYY" label (e.g. "August 2026") into a sortable number.
 * `localeCompare`/string comparison on these labels sorts alphabetically
 * ("August" > "July" > "September" as strings), not chronologically — this
 * is the fix for that.
 */
export function monthLabelSortKey(label: string): number {
  const [monthName, yearStr] = label.split(" ");
  const monthIndex = monthNames.indexOf(monthName);
  const year = Number(yearStr);
  if (monthIndex === -1 || Number.isNaN(year)) return 0;
  return year * 12 + monthIndex;
}

export function formatDateShort(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
}

export function formatRelativeAge(iso: string, now: Date = new Date()): string {
  const then = new Date(iso);
  const diffMs = now.getTime() - then.getTime();
  const days = Math.floor(diffMs / (1000 * 60 * 60 * 24));
  if (days <= 0) return "today";
  if (days === 1) return "1 day ago";
  if (days < 30) return `${days} days ago`;
  const months = Math.floor(days / 30);
  if (months === 1) return "1 month ago";
  return `${months} months ago`;
}

/** "August 2026" for a first-of-month ISO date, parsed as UTC so it never slips a month back. */
export function formatBillingMonth(billingMonth: string): string {
  return new Date(`${billingMonth}T00:00:00Z`).toLocaleDateString("en-US", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

/**
 * "Aug 15, 2026" for a bare `LocalDate` string (`YYYY-MM-DD`, e.g. a SIM Card's
 * `cancellationEffectiveDate`) — same UTC-parse fix {@link formatBillingMonth} already uses, since
 * a `LocalDate` has no time or zone of its own: feeding it through {@link formatDate} (which
 * assumes an `Instant`) parses it as UTC midnight, and `toLocaleDateString` with no explicit
 * `timeZone` can then roll it back a day in a negative-UTC-offset browser (design-review finding
 * on this feature's finisher pass — the Fleet tables and the draft Client Invoice's Postpaid SIM
 * table were showing the raw `2026-08-15` string instead).
 */
export function formatLocalDate(localDate: string): string {
  return new Date(`${localDate}T00:00:00Z`).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  });
}

/** How long something has waited, in whole days: "Today", "1 day", "12 days". */
export function formatWaitingTime(sinceIso: string, nowIso: string): string {
  const days = Math.floor((new Date(nowIso).getTime() - new Date(sinceIso).getTime()) / (1000 * 60 * 60 * 24));
  if (days <= 0) return "Today";
  return days === 1 ? "1 day" : `${days} days`;
}
