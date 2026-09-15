---
version: 1
slug: "frontend-app-manager-page-tsx"
primary_target: "frontend/app/manager/page.tsx"
related_targets: []
---

## Direction contract

**Mode: Operate**

THESIS: A financial-infrastructure control room, not a form-heavy admin CRUD
grid — the dashboard leads with the tenant's money and approval backlog,
refusing the generic entity-list-of-lists admin template.

OWN-WORLD: Near-white canvas / near-black in dark, deep-navy ink, one
electric-indigo accent (`#533afd`-family) reserved for primary stat numbers,
current selection and primary actions. Hairline 1px borders, soft shadow
only on the elevated review dialog. Compact 8px-radius controls everywhere;
true pill shape only on the indigo Review action. Inter (self-hosted
variable font) carries headings, labels, body and data; tabular figures on
every monetary value. Left rail fixed 240px, shared styling across surfaces.

STORY: A Manager opens on tenant-wide financial health — pending approvals
count, this month's billed and payout totals, Client/Agent/Contract counts —
then moves to Invoices to clear the approval queue oldest-first, reviewing
each in a focused dialog before approving.

FIRST VIEWPORT: Left rail (Dashboard / Clients / Agents / Contracts /
Invoices) at 240px; top bar with page title, viewer identity and theme
toggle; six stat cards in a responsive 3-column grid (pending approvals,
billed this month, payout this month, clients, agents, contracts), each
primary number in indigo; below, a "Pending approvals" preview list (oldest
first) linking through to the full Invoices table.

FORM: Brief-pinned direction (Stripe-inspired), no concept-seed roll —
new-work.md's standing rule that a user/brief-pinned direction beats the
roll.

FINISH: unreviewed and undocumented is unfinished; this build ends with the
finish review, the verdict, DESIGN.md, and every shipping raster carrying
its provenance.
