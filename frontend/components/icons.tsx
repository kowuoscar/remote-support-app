import type { SVGProps } from "react";

/**
 * One consistent stroke-based icon set (24px grid, 1.75 stroke, round caps
 * and joins) authored for this product — no emoji, no mixed libraries.
 */

type IconProps = SVGProps<SVGSVGElement>;

const base = {
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.75,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true,
};

export function IconDashboard(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="3.5" y="3.5" width="7.5" height="7.5" rx="1.5" />
      <rect x="13" y="3.5" width="7.5" height="4.5" rx="1.5" />
      <rect x="13" y="10" width="7.5" height="10.5" rx="1.5" />
      <rect x="3.5" y="13" width="7.5" height="7.5" rx="1.5" />
    </svg>
  );
}

export function IconClients(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="3.5" y="4" width="17" height="16" rx="1.5" />
      <path d="M8 9h8M8 12.5h8M8 16h5" />
    </svg>
  );
}

export function IconAgents(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="8.5" r="3.25" />
      <path d="M5 20c0-3.6 3.13-6 7-6s7 2.4 7 6" />
    </svg>
  );
}

export function IconContracts(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M7 3.5h7l4 4V20a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z" />
      <path d="M14 3.5V8h4" />
      <path d="M8.5 12.5h7M8.5 15.5h7M8.5 18h4" />
    </svg>
  );
}

export function IconInvoices(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M6 3.5h9l3 3V20a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z" />
      <path d="M9 9.5h6M9 13h6M9 16.5h3.5" />
      <circle cx="15.5" cy="16.5" r="0.1" />
    </svg>
  );
}

export function IconRequests(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M4.5 6.5A1.5 1.5 0 0 1 6 5h12a1.5 1.5 0 0 1 1.5 1.5v9A1.5 1.5 0 0 1 18 17H9l-4.5 4v-4.5" />
      <path d="M8 9.5h8M8 13h5" />
    </svg>
  );
}

export function IconFleet(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="7.5" y="2.5" width="9" height="19" rx="2" />
      <path d="M10.25 5.5h3.5" />
      <path d="M11 18.5h2" />
    </svg>
  );
}

export function IconMyInvoice(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="8" r="3.25" />
      <path d="M5.5 20c0-3.6 2.9-6 6.5-6" />
      <path d="M14.5 14.5h5.5v5.5h-5.5z" />
      <path d="M16 16.75h2.5" />
    </svg>
  );
}

export function IconChevronDown(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}

export function IconChevronRight(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="m9 6 6 6-6 6" />
    </svg>
  );
}

export function IconSearch(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="10.5" cy="10.5" r="6" />
      <path d="m20 20-4.35-4.35" />
    </svg>
  );
}

export function IconBell(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M6 17h12l-1.4-2.1a4 4 0 0 1-.6-2.13V10a4 4 0 0 0-8 0v2.77a4 4 0 0 1-.6 2.13L6 17Z" />
      <path d="M10 20a2 2 0 0 0 4 0" />
    </svg>
  );
}

export function IconCheckCircle(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="m8.5 12.25 2.4 2.4 4.6-5.3" />
    </svg>
  );
}

export function IconClock(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </svg>
  );
}

export function IconAlertTriangle(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M12 4 21 19.5H3L12 4Z" />
      <path d="M12 10v4" />
      <circle cx="12" cy="16.6" r="0.1" />
    </svg>
  );
}

export function IconPlus(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

export function IconArrowRight(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M4.5 12h15M13 5.5l6.5 6.5-6.5 6.5" />
    </svg>
  );
}

export function IconPaperclip(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M8 12.5 15 5.6a3.2 3.2 0 0 1 4.5 4.5L11 18.6a5.2 5.2 0 0 1-7.4-7.4L12 2.8" />
    </svg>
  );
}

export function IconInbox(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M4 12.5 6.2 5.4A1.5 1.5 0 0 1 7.65 4.3h8.7a1.5 1.5 0 0 1 1.45 1.1L20 12.5" />
      <path d="M4 12.5h4.6a1 1 0 0 1 .9.55l.6 1.2a1 1 0 0 0 .9.55h3.6a1 1 0 0 0 .9-.55l.6-1.2a1 1 0 0 1 .9-.55H20V18a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18v-5.5Z" />
    </svg>
  );
}

export function IconCoins(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <ellipse cx="9.5" cy="7.5" rx="6" ry="3.25" />
      <path d="M3.5 7.5V13c0 1.8 2.69 3.25 6 3.25S15.5 14.8 15.5 13V7.5" />
      <path d="M3.5 10.25c0 1.8 2.69 3.25 6 3.25s6-1.45 6-3.25" />
      <path d="M12 11.5c.58.9 1.72 1.5 3 1.5 2.2 0 4-1.7 4-3.75S17.2 5.5 15 5.5c-.5 0-.98.09-1.42.25" />
    </svg>
  );
}

export function IconWallet(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M4 7.5A1.5 1.5 0 0 1 5.5 6h11A1.5 1.5 0 0 1 18 7.5v1H5.5A1.5 1.5 0 0 1 4 7z" />
      <path d="M4 8.5V18a1.5 1.5 0 0 0 1.5 1.5h13A1.5 1.5 0 0 0 20 18v-7.5A1.5 1.5 0 0 0 18.5 9H5.5A1.5 1.5 0 0 1 4 7.5" />
      <circle cx="16.25" cy="13.75" r="1" />
    </svg>
  );
}

export function IconClose(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="m6 6 12 12M18 6 6 18" />
    </svg>
  );
}

export function IconFilter(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M4 6h16M7.5 12h9M10.5 18h3" />
    </svg>
  );
}

export function IconDownload(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M12 3.5v11.5M8 11l4 4 4-4" />
      <path d="M4.5 17.5V19a1.5 1.5 0 0 0 1.5 1.5h12a1.5 1.5 0 0 0 1.5-1.5v-1.5" />
    </svg>
  );
}

export function IconSmartphone(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="7" y="2.5" width="10" height="19" rx="2" />
      <path d="M11 18.25h2" />
    </svg>
  );
}

export function IconLogOut(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M9.5 4.5H6A1.5 1.5 0 0 0 4.5 6v12A1.5 1.5 0 0 0 6 19.5h3.5" />
      <path d="M14.5 8.5 19 12l-4.5 3.5" />
      <path d="M19 12H9.5" />
    </svg>
  );
}

export function IconSim(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M8 3.5h6l4 4V19a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 19V5A1.5 1.5 0 0 1 7.5 3.5Z" />
      <path d="M9.5 11h5M9.5 14h5M9.5 17h2.5" />
    </svg>
  );
}
