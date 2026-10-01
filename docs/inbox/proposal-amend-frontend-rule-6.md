---
id: proposal-amend-frontend-rule-6
type: proposal
status: open
blocks: []
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

Amend Frontend rule 6 in `docs/agents/coding-standards.md` so it covers only
whole-page (route-segment) failures?

Today it says: "An error that should show UI uses `error.tsx` at the nearest
route segment; … never swallowed and logged." Read literally, that covers a
single dashboard card that degrades to "Couldn't load…". But that is the
approved pattern: the Manager dashboard does it on `main`, and both dashboard
specs require it ("Regions fail alone").

## Recommendation

Yes. Add: "A region that degrades to its own 'Couldn't load…' state and logs
the failure is allowed; the rule governs failures that take out a whole route
segment." Raised by the standards reviewer on `real-agent-dashboard`.

## Blocks

Nothing.

## Meanwhile

Reviewers keep reading the rule as written.

## Answer
