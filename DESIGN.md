---
name: Remote Support Operate Console
description: A Restrained financial-infrastructure control room for an internal invoice-heavy Operate tool, shared across Manager, Agent and Client surfaces.
colors:
  canvas: "#ffffff"
  canvas-soft: "#f6f7fb"
  canvas-raised: "#ffffff"
  canvas-overlay: "#ffffff"
  ink: "#0b1220"
  ink-secondary: "#45506a"
  ink-mute: "#5c6b8e"
  ink-faint: "#8390af"
  hairline: "#e3e6ee"
  hairline-strong: "#d1d6e2"
  primary: "#533afd"
  primary-hover: "#4730e0"
  primary-press: "#3a24bd"
  on-primary: "#ffffff"
  primary-soft-bg: "#edebff"
  primary-soft-text: "#4730e0"
  success: "#0f7a53"
  success-bg: "#e4f5ed"
  warning: "#9a5b0a"
  warning-bg: "#fbf0dc"
  danger: "#c22a3e"
  danger-bg: "#fceaed"
  info: "#2f5fd6"
  info-bg: "#eaf0fd"
typography:
  body:
    fontFamily: "InterVariable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "1.5"
    letterSpacing: "normal"
  title:
    fontFamily: "InterVariable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "15px"
    fontWeight: 600
    lineHeight: "1.3"
    letterSpacing: "normal"
  stat:
    fontFamily: "InterVariable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "28px"
    fontWeight: 600
    lineHeight: "1.15"
    letterSpacing: "normal"
    fontFeature: "tnum, cv05"
  label:
    fontFamily: "InterVariable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: "1.3"
    letterSpacing: "0.02em"
rounded:
  control: "8px"
  card: "12px"
  pill: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  rail: "240px"
  topbar: "56px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.pill}"
    padding: "0 16px"
    height: "36px"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
  button-secondary:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "0 16px"
    height: "36px"
  button-row:
    backgroundColor: "{colors.primary-soft-bg}"
    textColor: "{colors.primary-soft-text}"
    rounded: "{rounded.control}"
    padding: "0 12px"
    height: "28px"
  badge:
    rounded: "{rounded.pill}"
    padding: "2px 10px"
    typography: "{typography.label}"
  card:
    backgroundColor: "{colors.canvas-raised}"
    rounded: "{rounded.card}"
  stat-card:
    backgroundColor: "{colors.canvas-raised}"
    rounded: "{rounded.card}"
    padding: "20px"
---

# Design System: Remote Support Operate Console

## Overview

**Creative North Star: "The Financial Control Room"**

This is an internal, invoice-heavy Operate tool for three roles (Manager, Agent, Client Tester) sharing one tenant. Its visual world is code-led and pinned against Stripe's console register, adapted — not copied — into a Restrained internal-tool key: Stripe's own marketing gradient-mesh and editorial color range never shipped here; what carried over is the discipline of a near-white canvas, deep-navy ink, one electric-indigo accent held in reserve, and hairline-bordered flat surfaces that let numbers and status do the talking. All three surfaces (Manager Console, Agent Console, Client Portal) share one shell, one component vocabulary and one token set; only navigation items and page composition differ per role, per each surface's direction contract.

The system refuses the generic entity-list-of-lists admin template and the marketing-style welcome dashboard. Each surface leads with money and status (pending approvals, billed/payout totals, open Requests, invoice state) ahead of raw CRUD tables. Dashboard stat cards are a deliberate, brief-earned exception to an hero-metric-template ban elsewhere in the system — confined to the dashboard, not proliferated onto every page.

**Key Characteristics:**
- Near-white / near-black canvas with deep-navy ink; one electric-indigo accent (`#533afd` light / `#6c5cec` dark) reserved for primary stat numbers, current selection and primary actions.
- Flat hairline-bordered surfaces at rest; soft shadow appears only on elevated overlays (dialogs, dropdown panels).
- Self-hosted Inter variable font end to end; tabular figures on every monetary and numeric value via the `.tnum` utility.
- Compact 8px-radius controls everywhere; true pill shape reserved for the single primary commit action per screen (Approve, Send Invoice, Submit Request).
- One shared left rail (240px, off-canvas drawer below `md`) and top bar across all three surfaces; only the nav item list and page content vary by role.
- Custom stroke-based SVG icon set (24px grid, 1.75 stroke, round caps) — no icon fonts, no emoji.

## Colors

A restrained palette: two neutral scales (canvas, ink) carry nearly everything; one indigo accent and four semantic-state colors are the only saturated colors in the system.

### Primary
- **Electric Indigo** (`#533afd` light / `#6c5cec` dark): primary stat numbers, current nav selection, the single pill primary-action button (Approve, Send Invoice, Submit Request), focus ring, caret, and text selection. Never used for section headers, decorative fills, or more than one accent role per screen.

### Neutral
- **Canvas** (`#ffffff` light / `#0a0d14` dark): page background.
- **Canvas Soft** (`#f6f7fb` light / `#10141e` dark): nav rail background, table header rows, hover fills, skeleton placeholders.
- **Canvas Raised / Overlay** (`#ffffff` / `#141926`–`#171d2b` dark): card and dialog/dropdown surfaces.
- **Ink** (`#0b1220` light / `#f3f5f9` dark): primary text.
- **Ink Secondary** (`#45506a` / `#b7bfd1` dark): secondary text, inactive nav labels.
- **Ink Mute** (`#5c6b8e` / `#838ca1` dark): captions, stat labels, muted icons.
- **Ink Faint** (`#8390af` / `#546283` dark): placeholder text.
- **Hairline / Hairline Strong** (`#e3e6ee`/`#d1d6e2` light, `#232a3a`/`#2d3547` dark): the system's only border colors — every border in the app resolves through these two tokens.

### Semantic
- **Success** (`#0f7a53` / bg `#e4f5ed`; dark `#35c290` / bg `rgba(53,194,144,.14)`): Completed/Active/Approved/Paid states.
- **Warning** (`#9a5b0a` / bg `#fbf0dc`; dark `#e3a33e`): In Progress, Awaiting approval, In Repair, and the paused Carrier invoice files notice. The side list's "Sent back" mark is also warning, but carries the alert-triangle icon (`IconAlertTriangle`, `h-3 w-3`) in place of the dot, so it never reads as "Awaiting approval".
- **Danger** (`#c22a3e` / bg `#fceaed`; dark `#f16b7a`): destructive actions, negative money values.
- **Info** (`#2f5fd6` / bg `#eaf0fd`; dark `#6f9bff`): Submitted, invoice Approved-not-yet-paid.

### Named Rules
**The Reserved Indigo Rule.** Indigo appears in exactly three roles per screen — the primary stat number, current selection/active nav, and the one pill-shaped primary action — never as a decorative fill, header rule, or secondary accent. Its rarity is the point of the "financial control room" register.

**The Two-Border-Tokens Rule.** Every border in the system draws from `hairline` or `hairline-strong` only; no ad hoc border colors.

## Typography

**Body/Display Font:** InterVariable (self-hosted `next/font/local`, weights 100–900, roman + italic; fallback `ui-sans-serif, system-ui, sans-serif`).

**Character:** One family for headings, labels, body and data — a control-room register, not an editorial pairing. `cv05`/`cv11` font-feature settings are enabled globally for a cleaner numeral and figure set.

### Hierarchy
- **Title** (600, 15px, tight leading): page titles in the top bar.
- **Stat** (600, 28px, tight leading, tabular figures): the one primary number per stat card.
- **Body** (400–500, 14px, 1.5 line-height): table cells, form values, page copy.
- **Label** (500, 12–13px, slight tracking, often uppercase): stat-card labels, table column headers, badges.

### Named Rules
**The Tabular Everywhere Rule.** Every monetary or numeric value (`Money`, stat-card values) carries `font-variant-numeric: tabular-nums` via the shared `.tnum` utility — no exceptions, so columns of numbers always align.

## Layout

Shared three-surface app shell: a fixed 240px left nav rail (`NavRail`, same width/styling/active-state treatment on all three surfaces, only the item list changes per role) and a sticky 56px top bar (title, viewer identity chip, theme toggle). The chip is the trigger for the viewer's actions **Menu** (self-service-password-change spec): Change password and Log out live inside it, and no standalone log-out control sits beside it. Below the `md` breakpoint the rail becomes an off-canvas drawer (fixed, translated off-screen, slid in over a `bg-ink/40` backdrop via a mobile-nav toggle in the top bar) rather than collapsing to icons-only. At `md`+ the rail is `sticky`/full-viewport-height with no internal scroll container, so the document — not an inner `<main>` — scrolls, keeping the rail and top bar in view. The one inner scroll container is the **side list** (below), on the pages that have one; the document still scrolls the area beside it.

Dashboards use a responsive stat-card grid (3-column on Manager, up to 4 on Agent, 2 on Client — count follows the surface's own metrics, not a fixed template) followed by a linking preview list into the relevant full table. Tables scroll horizontally on narrow viewports (`min-w-[720px]` inside an `overflow-x-auto` wrapper) rather than reflowing to cards (in the Fleet's SIM Cards table, Plan and Carrier names are cut with "…" at their column's width, the full name in `title`, so a long name never widens the table; a name that fits its column is never cut) — except the Agent's Client Invoice lines (Postpaid SIM Cards, Fee lines), which below `xl` reflow to a one-column list of rows (what and amount, then details and actions), keeping their table semantics; these two line tables carry no 720px minimum at any width, so they fit their card at `xl` too (client-invoices-layout-fixes spec). The Fleet tables (SIM Cards, Smartphones; `Table dense`) carry that minimum below `xl` only: from `xl` up the detail beside the side list is 734px at 1280 and 894px at 1440, narrower than the columns need at 720, so the table fits its card: cells are padded 10px, not 16px, the status and its action (Retire, Reactivate, the Smartphone's status select) share the Status cell, one above the other, with no "Change status" column, and a row's controls (the number, the plan and their Edit buttons) wrap under their value when the column is squeezed. A SIM number never breaks across lines. At `xl` and 1440 no Fleet table scrolls sideways, with a 60-character Plan name too (measured by `tests/visual/fleet-table-fit.spec.ts`); below `xl`, where there is no column, it keeps its 720px minimum and scrolls inside its wrapper as any table does, and the page never scrolls sideways.

Spacing runs a tight, consistent rhythm: `4/8/12/16/20px` steps. Stat cards and cards use `20px` (p-5) internal padding; table cells use `16px` horizontally and `10px`–`12px` vertically (px-4, py-2.5–3), and `10px` horizontally in the Fleet tables (`Table dense`); nav items use `12px`/`8px` (px-3/py-2); button height is `36px` (md) or `28px` (sm, row actions).

## Elevation & Depth

Flat by default. Cards, tables and the nav rail carry only a 1px hairline border, no shadow. A soft, diffuse shadow (`--shadow-elevated`: `0 12px 32px rgba(13,22,41,.14), 0 2px 8px rgba(13,22,41,.06)` light; deeper black-based values in dark) appears exclusively on the elevated overlay layer — review dialogs, the Contract switcher's open dropdown panel, the viewer chip's open Menu panel — via the `Panel` component and `shadow-elevated` utility. No hard offset or neobrutalist-style shadows anywhere in the system.

### Shadow Vocabulary
- **Elevated** (`shadow-elevated`): dialogs, dropdown/listbox panels, the viewer chip's open Menu panel — anything temporarily layered above the page.
- **Elevated Strong** (`shadow-elevated-strong`): reserved for higher-emphasis overlays (defined in tokens; not yet exercised by a shipped component beyond the two above).

### Named Rules
**The Flat-At-Rest Rule.** Surfaces are flat (hairline border only) at rest. Shadow is reserved for the overlay z-layer, never applied to inline cards or table rows.

**The Menu Returns Focus Rule.** A Menu (self-service-password-change spec, `viewer-chip-menu` ticket) always returns focus to its trigger when it closes — by Escape, by an outside press, or by activating an item — and is dismissible by both Escape and an outside press while open, with those listeners bound only while it is open. `ContractSwitcher` predates this rule and does not follow it; a Menu built after this rule exists always does.

## Shapes

Two radius steps carry the whole system: `8px` (`rounded-lg`, Tailwind) for every control — buttons (except primary), inputs, badges' containers, nav items, table wrappers' inner elements — and `12px` (`rounded-xl`) for cards, panels, dialogs and dropdown surfaces. Pill (`rounded-full`) is reserved for exactly two uses: the primary-action button and status badges. Borders are always 1px, drawn from the two hairline tokens; no double borders, no colored borders outside semantic-state components.

### Named Rules
**The Pill-Is-Primary Rule.** Full-pill radius signals "the one primary action on this screen" (or a status badge). Any other control — including a "row" action styled with the soft-indigo tone — stays at the 8px control radius. A screen with two pill buttons is a defect, not a variant.

## Components

### Buttons
- **Shape:** primary is a true pill (`rounded-full`); secondary/ghost/danger/row are 8px radius (`rounded-lg`).
- **Primary:** `bg-primary` / `text-on-primary`, `hover:bg-primary-hover`, `active:bg-primary-press`, disabled at 40% opacity. Height 36px (md) / 28px (sm), horizontal padding 16px (md) / 12px (sm).
- **Secondary:** `bg-canvas`, `text-ink`, `border-hairline-strong`, hover fills `canvas-soft`.
- **Ghost:** transparent, `text-ink-secondary`, hover fills `canvas-soft` and darkens text to `ink`.
- **Danger:** `bg-danger`, white text, brightness-shift on hover/active.
- **Row** (inline record actions): soft-indigo tone (`primary-soft-bg`/`primary-soft-text`) at the 8px control radius — indigo used here as a low-emphasis tint, not the reserved primary-action pill.
- **Inline corrections** (Fleet units, Client Invoice lines): the value with a `ghost` `sm` Edit beside it. Editing is the field, a `row` Save and a `ghost` Cancel on one line, with the refusal below.

### Carrier invoice check mark
A Postpaid SIM line whose carrier invoice is attached carries `IconCheckCircle` (`h-3.5`, `success`) after its Carrier label, non-interactive, named "Carrier invoice attached". A file row's Remove is a quiet `ink-mute` text action whose inline confirm is danger-toned: "Remove this file?", a `ghost` `sm` Cancel and a `danger` `sm` Remove, no modal.

### Paused files section
When file storage is unavailable, the Carrier invoice files section keeps every file name (`ink`) with its paperclip, turns each Download into non-interactive `ink-mute` text (`aria-disabled`, no `href`, `cursor-not-allowed`), disables "Attach carrier invoice" and, where it is offered, each Remove with the secondary button's disabled look, and adds one `warning`-tone `text-label-sm` line with `IconAlertTriangle` under the heading (plain paragraph, not an alert). No banner, no card, no new token. A file retention has deleted keeps its row, paperclip and name (`ink`) and shows plain `ink-mute` `text-label-sm` "Deleted on {date}" where Download was (no icon, not a link, not focusable, no `aria-disabled`), whether storage is up or down; the notice appears only when something is really paused (the attach control, or a file not deleted).

### Badges
- **Style:** pill-shaped (`rounded-full`), small dot + label, `px-2.5 py-0.5`, `text-xs font-medium`. Six tones map 1:1 to semantic colors (`success`/`warning`/`danger`/`info`/`neutral`/`primary`), driven centrally by `lib/status.ts`'s status→tone maps (Request, Smartphone, SIM, Client Invoice, Agent Invoice statuses) so status color is never chosen ad hoc per screen.

### Cards / Containers
- **Corner Style:** 12px radius (`rounded-xl`).
- **Background:** `Card` = `canvas-raised`, flat hairline border, no shadow. `Panel` = `canvas-overlay`, same border, plus `shadow-elevated` — the only elevated variant.
- **Internal Padding:** 20px (stat cards, `p-5`); dialogs and dropdowns vary by content.

### Inputs / Fields
- **Style:** 8px radius, `hairline-strong` border, `canvas` background, 36px height, left-icon inset (e.g. `SearchInput`'s 16px leading search glyph).
- **Focus:** border shifts to `primary` on `:focus-visible` (no separate glow layer beyond the global 2px `focus-ring` outline used on non-input controls).

### One-time password reveal
The success step of every dialog that generates a password (`GeneratedPasswordReveal`): a heading and a line naming the email; a read-only **Generated password** field on `canvas-soft` (the read-only surface), `.tnum` with wide tracking, focused and selected on mount; a secondary `sm` **Copy password** button beside it with a polite status line; a plain warning that the password won't be shown again; and **Done** as the step's only pill. No token changes.

### Navigation
- **Style:** 240px fixed rail, `canvas-soft` background, 1px hairline right border. Items are 8px-radius rows (`px-3 py-2`), `text-sm font-medium`; inactive items are `ink-secondary` with a custom SVG icon in `ink-mute`; the active item takes the soft-indigo tone (`primary-soft-bg`/`primary-soft-text`) on both icon and label — indigo signaling current selection, never a full-saturation fill.
- **Mobile:** below `md`, the rail becomes a fixed off-canvas drawer sliding in from the left over a blurred dark backdrop, opened via a top-bar toggle and closed on backdrop click, Escape, or item selection.
- **Item list, per role** (carrier-catalog ticket `agent-maintains-carriers`; manager-approves-requests ticket adds Manager's own Requests item; agent-stock ticket adds both Consoles' Stock item): Agent Console — Dashboard, Requests, Fleet, Stock, Carriers, Client Invoices, My Invoice; Manager Console — Dashboard, Requests, Clients, Agents, Contracts, Stock, Carriers, Invoices; Client Portal unchanged (no Carriers or Stock access).

### Contract Switcher (signature component)
A dropdown — explicitly never a tab set — for Contract scoping: it scopes Requests/Fleet and, below `xl` (1280px), Client Invoices (Agent Console), or the dashboard and lists (Client Portal, multi-Contract Clients) to one Contract at a time. At `xl` and wider the side list replaces it on Client Invoices and on Fleet (the Agent, and a Tester with several Contracts; see below). Below `xl` it is also every side list's dropdown, with a per-page icon (Contracts by default, on the Fleet pages; Carriers on the Carriers pages; Clients on the Manager's Contracts page; Agents on the Manager's Invoices page, each option's meta line its count, "3 waiting" or "Nothing waiting"): the options come from the page, and a Carrier's carries "Archived" as its meta line. Requests keeps it at every width. Collapses to a static, non-interactive hairline chip when the viewer holds only one Contract (or one Carrier). Open state is an `Panel`-style listbox (12px radius, `shadow-elevated`) with the current selection marked by the soft-indigo tone, matching nav's active-state language.

### Month bar
The Agent's Client Invoices page opens with one hairline `Card` row (`px-4 py-3`), first in the page's lead area (above "Sent back to you", so above the Contract Switcher below `xl`), absent when the Agent has no Contract. The month and its state read on the left as one sentence ("September 2026 · 1 of 2 ready", `text-sm` 600, the count in `ink-secondary` `tnum`), the action on the right; below `md` the button wraps under the text, right-aligned.
- **Unsent:** a primary `sm` pill, "Send all {n} to Manager" ("Send 1 to Manager" for one Contract). It is the page's one primary pill while the month is unsent (the cards offer no Send then); once the month is sent the bar holds no button, and a sent-back or late card's Send is the one pill. While any invoice is not ready the pill takes the primary's 40% disabled look and one `text-label-sm` `ink-mute` line says how many still need their carrier invoices, linked by `aria-describedby`: a plain sentence, no danger tone, because an unfinished month is not an error. The confirm is the Client Invoice Send's two-step inline confirm (Back `ghost` `sm`, "Confirm send all {n}" primary `sm`), with a refusal below it as a `danger` `text-label-sm` alert.
- **Sent:** `IconCheckCircle` (`h-3.5`, `success`) and one sentence, "Sent to Manager on {date}", no badge and no button. The date is the month's first send and stays through a send-back and a late Contract.
- **Incomplete:** when an invoice failed to load, "Couldn't load every invoice." with the pill disabled and "Reload the page to send the month." as its note: a month the page could not fully see is not sent from it.

### Month review
The Manager's Invoices page, under an Agent, opens with one hairline `Card` row (`px-4 py-3`), a region named "Month", mirroring the Agent's Month bar. The month (`h2`, `text-sm` 600) and one sentence read on the left, the Month select under them, the action on the right; below `md` the button wraps under the text, right-aligned. The sentence is "Sent to you on {date} · {k} of {n} to review", then once nothing is left "{a} of {n} approved", plus " · {s} sent back" when any (the count in `ink-secondary` `tnum`).
- **One pill:** with {k} > 0 the header holds a primary `sm` pill, "Approve the {k} left to review", the screen's one primary pill; row "Review →" links keep the row tone. With {k} = 0 the header holds no button. The confirm is the Client Invoice Send's two-step inline confirm (Back `ghost` `sm`, "Confirm approve {k}" primary `sm`, "Approving…" while pending), with a refusal below it as a `danger` `text-label-sm` alert.
- **Next invoice:** on a Client Invoice's detail page, right-aligned above the invoice, a `secondary` `sm` link "Next invoice →" (accessible name "Next invoice: {Client}") opens the next invoice still to review in that Agent's month; with none, `text-label` `ink-mute` "Nothing else to review in {Month Year}." takes its place, and a failed month read shows neither. The breadcrumb reads "Invoices › {Agent} › {Client}", the Agent crumb opening that Agent's month.
- **Manager status labels:** the rows' `Badge` reads "To review" (warning, dot), "Approved" (success, dot) or "Sent back" (warning, `IconAlertTriangle` in place of the dot).

### Side list
A shared component (`SideList` in a `SideListLayout`; side-lists-and-tabs spec): a column of links beside the detail they select, at `xl` (1280px) and wider only. It exists on list-and-detail pages where seeing every item at once beats a dropdown; below `xl` the rail plus the column would leave the detail under 720px, so the Contract Switcher, given a per-page icon, takes its place, and exactly one of the two is displayed at any width. Its users: Client Invoices (the Agent's Contracts, by Client name, with the month's invoice status); Fleet (the Agent's "Clients", by Client name with the Country only on a shared name and no badge; a Tester with several Contracts gets "Contracts", one row per Contract by Country, and with one Contract no column at all, only today's static chip above the tabs); the Manager's Contracts ("Clients": one row per Client that holds a Contract, by name, no badge; the detail area reads the Client heading (an `h2` at `text-sm` 600, like the Carrier's name), an "Agent" select over that Client's Contracts, "{Country} · {currency}", the current-month invoice summary, then the tabbed Fleet, whose "Add SIM card" and "Add smartphone" are `secondary` `sm` buttons at the right of the tab strip, so "New Contract" above the column is the screen's one pill; below `xl` the order is "New Contract", the Clients dropdown, the Agent select); the Manager's Invoices ("Agents": "All Agents" first, which is the Review Queue, then every Agent of the tenant by name; the count of invoices waiting on the Manager sits in the badge column as `ink-mute` `tnum` text, not a `Badge`, and reads "—" when none, with "All Agents" carrying the total; the detail area is the picked Agent's month, or the Review Queue, whose table is `dense` so it fits beside the column; a tenant with no Agent, or a failed Agents read, shows no column and no dropdown, only the queue); and Carriers, for the Agent and the Manager (every Carrier of the Country, in the server's order: active first, then by name; archived ones muted, at the bottom, badged "Archived"). On Carriers, "Add carrier", "{n} active" and the Manager's Country filter sit in a header above the column (above the dropdown below `xl`); a Country with no Carriers, or a failed catalog, shows no column and no dropdown, only the header and the state across the detail area.
- **Shape:** `nav` landmark named by its heading ("Clients", "Carriers", "Contracts") with a small `text-label` / `ink-mute` heading, one link per item in the page's column order (on Client Invoices, alphabetical: Client name, then Country). A plain click swaps the detail in place and writes the selection to the address without a history entry; a modified click opens the link's `href`.
- **Column:** 256px wide, flush against the rail, `canvas` (not the rail's `canvas-soft`) with one `hairline` right border and no shadow. Sticky directly under the 56px top bar, as tall as the viewport minus that bar, scrolling on its own.
- **Row:** `px-3 py-2`, 8px radius, `text-sm font-medium`, `ink-secondary` at rest and `canvas-soft` on hover. A two-column grid (name, then badges): the name is on the left of line 1 on one line, cut with "…" when too long (full name in `title`), so it never widens the column; the month's Client Invoice status as the existing `Badge` of `clientInvoiceBadge` (Draft, Awaiting approval, Approved, Sent back — the same label and tone as the invoice card) sits on the right of the same line, at least 8px from the name and never shrinking, on every row except one whose invoice failed to load, which shows no badge. A second line appears only when there is something for it: the Country (`text-label-sm`, `ink-mute`, under the name) when two of the Agent's Contracts share a Client name, and, under the status and right-aligned with it, a second, warning-toned `Badge` reading "Sent back" with `IconAlertTriangle` (`h-3 w-3`) in place of the dot, on a Contract the Agent's "Sent back to you" list holds an invoice of (any billing month): the mark that tells the Agent who needs correcting. Never twice: a row whose own badge already reads "Sent back" shows no mark; a row whose invoice failed to load shows the mark alone, on line 1 in the status's place. On a row whose current-month invoice is a draft still to send, line 2 left reads '✓ Ready' (`IconCheckCircle` `h-3`, `success`) or '{n} files missing' (`ink-mute`), after the Country when shown; never on a sent, approved or unloaded row. The accessible name order is name, Country, status, mark, readiness: the grid places the parts, the DOM order does not move.
- **Carrier rows:** the name only, with the neutral "Archived" `Badge` on an archived Carrier, whose name reads `ink-mute` while it is not the selected row; the accessible name is the name, then "Archived". The selected Carrier is one Card across the detail area: its name with Rename and Archive (an archived one shows the muted name, "Archived {date}" and the badge, and no actions), the "Show archived" toggle (counting only that Carrier's archived offers), and its Topup Options and Postpaid Plans side by side from `md` up.
- **Selected row:** `aria-current="true"` and the soft-indigo tone (`primary-soft-bg` / `primary-soft-text`), the same "current selection" role as the rail's active item and the Switcher's selected option.

### Tabs
A shared segmented tablist (`Tabs`; side-lists-and-tabs spec) that switches between the tables of one record. Its users are the three Fleet views (the Agent's, the Tester's and the Manager's Contracts page), where the card's header row *is* the strip: "SIM Cards {n}" and "Smartphones {n}", each with its icon, so no table title repeats a tab's label. It never scopes a Contract: that stays the Switcher's and the side list's job.
- **Look:** the strip the Requests status filter already uses: a `canvas-soft` track with a hairline border and `p-1`, the selected tab a raised `canvas-raised` segment (lighter than the track in dark mode, where `canvas` would sink below it) with `shadow-sm`, a `hairline-strong` ring and `rounded-md`, the others `ink-mute`. The selected tab is not indigo: the side list's selected row already holds the current-selection indigo. Each tab carries a label and its count in `ink-mute` `tnum`.
- **Behaviour:** a WAI-ARIA tablist named "Fleet", with a roving `tabindex`; Left and Right move and wrap, Home and End jump, and focus selects (automatic activation, since both tables' data is already on the page). One panel is rendered at a time, labelled by its tab. The selected tab is written to the address as `?tab=smartphones` (absent for SIM Cards, the default and first tab), without a history entry, and is kept when the Contract changes.

### Menu
The system's first actions menu (self-service-password-change spec, `viewer-chip-menu` ticket): a trigger-plus-panel pattern that runs an action rather than scoping data — the distinction from Contract Switcher above, which the two must never converge on. Today's one instance is the top bar's viewer identity chip, holding *Change password* and *Log out*.

- **Trigger:** any button carrying `aria-haspopup="menu"` and `aria-expanded`; the viewer chip's own trigger keeps its existing hairline-chip look (`rounded-full`, `hairline` border, `canvas-soft` background) rather than adopting Contract Switcher's 220px labelled-control shape, and is never hidden below `sm`.
- **Panel:** pinned to Contract Switcher's open-panel appearance only, not its code — `rounded-xl` (12px), `canvas-overlay`, hairline border, `shadow-elevated`; right-aligned to the trigger and width-capped so it cannot overflow the viewport.
- **Items:** `role="menuitem"` buttons at the 8px control radius, the rail's own row rhythm (`px-3 py-2`, `text-sm font-medium`), `ink-secondary` at rest, `canvas-soft` on hover. No indigo fill — the Reserved Indigo Rule does not allot indigo to a menu item.
- **Behaviour** (see the Elevation section's named rule): focus moves into the panel on open and back to the trigger on close by any means; arrow keys cycle between items; Escape and an outside pointer press dismiss it, with those listeners bound only while it is open. Contract Switcher predates this component and has none of this behaviour — it is visual prior art only, never a behavioural one.

## Do's and Don'ts

### Do:
- **Do** reserve indigo for exactly the primary stat number, current selection, and the one pill primary-action per screen.
- **Do** apply `.tnum` tabular figures to every monetary and numeric display value.
- **Do** use the 8px control radius for every button, input, and row element except the single primary pill action.
- **Do** keep the nav rail width, item styling, and active-state treatment identical across Manager, Agent and Client surfaces — only the item list changes per role.
- **Do** use a dropdown, never a tab set, when a screen needs Contract-scoping (Agent, Client with multiple Contracts) — at `xl` and wider, a side list instead on the pages that have one (Client Invoices, Fleet, the Manager's Contracts, Carriers; client-invoices-side-list and side-lists-and-tabs specs). **Do** use tabs only to switch between one Contract's tables, never between Contracts.
- **Do** keep shadow confined to the overlay layer (dialogs, open dropdown panels); cards and table rows stay flat.

### Don't:
- **Don't** apply shadow to inline cards, stat cards, or table rows — shadow signals "elevated/temporary," not "static content."
- **Don't** add a second pill-shaped control to a screen that already has a primary action.
- **Don't** introduce a border color outside the `hairline`/`hairline-strong` tokens.
- **Don't** use icon fonts, emoji, or a second icon library alongside the custom stroke-based SVG set.
- **Don't** surface a draft invoice to the Client Portal — invoice visibility rules are a product constraint the visual system must respect, not a styling choice to route around.
