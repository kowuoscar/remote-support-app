---
id: ui-side-lists-fix-pass-two
type: question
status: answered
blocks: [ui-side-lists-and-tabs]
created: 2026-10-16
---

<!-- sdlc:template inbox-item 1 -->

## Question

The one fix pass the loop allows did not clear everything. May one more fix
pass run, for these?

1. **The refresh bug is still there (F23).** On the Agent Fleet, correct a
   SIM's plan, then switch to Smartphones and back. The row still shows the
   old plan, and stays that way until you reload: 6 runs out of 6 on a
   production build. Picking another Client does the same (5 out of 6). The
   save does reach the server. The fix made by the first pass passed its
   unit tests but doesn't work in a real browser.
2. **Two leftover 12px text sizes (F24, F25)** from the first fix pass.
3. **Whatever you choose** in `ui-side-lists-fleet-table-layout`.

## Recommendation

Yes. This time the fix pass must prove the refresh fix in a real browser:
the walkthrough agent's script becomes an end-to-end test that fails before
the fix and passes after it. If the address write can't be made safe, the
fallback is to show the saved values from the save's own response, so the
row is right whatever the page refresh does.

## Blocks

`ui-side-lists-and-tabs` (F23, F24, F25).

## Meanwhile

Nothing else runs.

## Answer

Ok (the human, 2026-10-16): run one more fix pass, as recommended.
