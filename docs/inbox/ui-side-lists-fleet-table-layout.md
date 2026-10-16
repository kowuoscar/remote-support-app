---
id: ui-side-lists-fleet-table-layout
type: question
status: open
blocks: [ui-side-lists-and-tabs]
created: 2026-10-16
---

<!-- sdlc:template inbox-item 1 -->

## Question

With the side list beside it, the Fleet table no longer fit at 1280–1440px:
"Installed in" was cut off and Retire / Reactivate could only be reached by
scrolling sideways. The fix pass made the table fit by:

- moving Retire / Reactivate under the status badge, so the separate
  "Change status" column is gone;
- cutting long Plan and Carrier names at 120px instead of 192px.

The spec said Fleet tables keep their sideways scroll and the 192px cut, so
this needs your decision. Which do you want?

- **(A) Keep the fitted desktop table**, with two corrections. On a phone
  the table scrolls sideways again instead of squeezing (today it breaks a
  Tester's numbers over three lines). Long names are cut at the column's own
  width, not a fixed 120px, so a Tester's Plan isn't cut when there is room.
- **(B) Go back to the separate "Change status" column and the 192px cut**,
  and let the table scroll sideways on desktop too.

## Recommendation

(A). Everything is visible on desktop without scrolling, which is what you
asked for on the Plan column. Retire sits right under the status it changes.

## Blocks

`ui-side-lists-and-tabs` (findings F28, F31), together with
`ui-side-lists-fix-pass-two`.

## Meanwhile

Nothing else runs: `text-size-lint-rule` is held at your request.

## Answer
