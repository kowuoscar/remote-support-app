---
id: real-client-dashboard-two-findings-after-fix
type: question
status: open
blocks: [real-client-dashboard]
created: 2026-10-02
---

<!-- sdlc:template inbox-item 1 -->

## Question

The client dashboard is built and both original blocking findings are fixed and
confirmed. But the fix itself tripped two written rules (F22, F23 in
`docs/features/real-client-dashboard/findings.json`). The loop allows only one
fix pass, so you decide.

**F22 — a hand-picked width with no comment.** To stop the Contract name being
cut off on phones, the fix gave the amount a minimum width written as
`min-w-[10rem]`. The rules say to use the design scale (`min-w-40` is the same
10rem) or explain why not. One-line fix.

**F23 — screenshots changed without touching `DESIGN.md`.** That same fix moved
the two phone screenshots the visual tests compare against. `docs/agents/frontend.md`
says, in one place, "a golden changes only with `DESIGN.md`". But in another
it says a visual change is fine if it *follows* `DESIGN.md`, which this one
does. The earlier tickets on this branch did exactly the same and nobody
flagged them. The two sentences disagree.

**Proposed:** authorise one extra fix pass for F22 only (swap to `min-w-40`,
re-run the tests); dismiss F23, and reword the rule so a screenshot may move
with a change that follows `DESIGN.md`, but never on its own.

## Answer

