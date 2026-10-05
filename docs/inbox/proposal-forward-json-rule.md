---
id: proposal-forward-json-rule
type: proposal
status: answered
blocks: []
created: 2026-10-03
---

<!-- sdlc:template inbox-item 1 -->

## Question

May I amend coding rule Frontend 13 so that it covers plain JSON routes?

Today the rule says each server route must reuse a shared forwarder,
`forwardBinary` or `forwardSecretJson`. But the most common kind of route
returns plain JSON, and neither forwarder fits it. So every new route, like
the one invoice editing just added, ends up hand-writing the very shape the
rule forbids.

**Proposed:** add a `forwardJson` helper to `frontend/lib/api` and name it in
the rule. The next feature that touches a route switches it over. This
changes no behaviour.

## Recommendation

Accept. It's raised by the standards reviewer, and the hand-written routes
are already recorded as debt.

## Blocks

Nothing.

## Meanwhile

Nothing waits on it.

## Answer

Ok (2026-10-05): name `forwardJson` in Frontend 13; old hand-written routes switch when next touched.
