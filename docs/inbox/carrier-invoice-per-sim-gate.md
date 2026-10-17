---
id: carrier-invoice-per-sim-gate
type: question
status: open
blocks: [carrier-invoice-per-sim]
created: 2026-10-18
---

<!-- sdlc:template inbox-item 1 -->

## Question

After the one fix pass, the merge gate is blocked by F11, F27, F28 and F29
(`docs/features/carrier-invoice-per-sim/findings.json`). Everything else is
confirmed; walkthrough steps 1–14 passed.

1. **F11 (rule Backend 6).** The new demo-data test cannot use the shared
   integration-test base: that base runs the app with test settings and one
   database shared by every test, and booting the demo data would write into
   it. `DemoDataLoaderApiTest` on main already breaks the rule the same way.
   Exempt tests that boot the demo data?
2. **F27–F29 (design, raised at re-review).** Three small visual fixes: the
   four new "Remove confirm open" screenshots were captured mid-scroll (top
   bar drawn over the card); at phone width the Attach/Send column doesn't sit
   flush right; at phone width the Manager's check mark wraps alone under its
   Carrier label. Authorize one more fix pass for them?

## Recommendation

1. Yes: add the exemption to Backend 6 and dismiss F11 on your authority.
2. Yes: one more fix pass, then re-review and merge.

## Blocks

`carrier-invoice-per-sim`.

## Meanwhile

PR #33 stays draft.

## Answer
