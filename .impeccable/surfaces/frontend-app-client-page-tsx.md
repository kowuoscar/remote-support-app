---
version: 1
slug: "frontend-app-client-page-tsx"
primary_target: "frontend/app/client/page.tsx"
related_targets: []
---

## Direction contract

**Mode: Operate**

THESIS: A Client Tester's trust surface for what they're being charged and
asked to service — a read-mostly Operate view with exactly one prominent
write action (Submit Request), refusing the marketing-style welcome
dashboard and refusing to surface draft invoices before the Agent sends
them.

OWN-WORLD: Same shared Restrained world as Manager Console and Agent
Console — near-white / near-black canvas, deep-navy ink, one
electric-indigo accent on primary stat numbers and primary actions,
hairline borders, self-hosted Inter with tabular figures, 8px-radius
controls. Submit Request is the one true pill CTA on this surface, kept
prominent on Fleet and repeated on Requests.

STORY: A Tester opens on active Fleet count, open Requests count and the
latest sent Client Invoice per Contract, then drills into Fleet / Requests /
Invoices — scoped by the same Contract-switcher dropdown pattern as Agent
Console whenever their Client holds more than one Contract — and can submit
a new Request from Fleet or Requests at any time.

FIRST VIEWPORT: Left rail (Dashboard / Fleet / Requests / Invoices) at
240px, same styling as the other two surfaces; two stat cards (active
Fleet, open Requests); below, a "Latest Client Invoice" panel listing one
row per Contract (status badge + amount), never a draft.

FORM: Brief-pinned direction (Stripe-inspired), no concept-seed roll —
new-work.md's standing rule that a user/brief-pinned direction beats the
roll.

FINISH: unreviewed and undocumented is unfinished; this build ends with the
finish review, the verdict, DESIGN.md, and every shipping raster carrying
its provenance.
