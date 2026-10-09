---
id: approve-text-size-lint-rule
type: approval
status: open
blocks: [text-size-lint-rule]
created: 2026-10-09
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the spec for the text-size lint rule?
`docs/features/text-size-lint-rule/spec.md`.

Review keeps flagging hand-written small text sizes (`text-[12px]`,
`text-[13px]`). This feature makes the build catch them instead.

1. The 203 existing ones across 79 files are swapped for the named sizes
   (`text-label-sm`, `text-label`). The named sizes are exactly 12px and 13px,
   so no screen changes by a single pixel. The screenshot suite proves it.
2. Then `npm run lint`, and therefore `verify`, fails on any new one,
   anywhere in the frontend, with a message naming the size to use instead.

These are choices made without you; approving accepts them:
1. **The whole frontend, not just the files a branch touches.** The epic
   said "files a branch touches". Once the old ones are gone, checking
   everywhere is simpler and stricter.
2. **11px is left alone** (32 places), as are one 15px and one 28px. Your rule
   names only 12px and 13px, and 11px is smaller than any named size. They
   could be a later clean-up.
3. **Your rule Frontend 8 is reworded** to say these sizes are never written
   and lint enforces it. The "known debt in untouched lines" clause goes,
   since there's none left.
4. No new dependency: it uses ESLint's built-in rule.

## Recommendation

Approve. It removes a review fix pass that every recent feature has paid,
for a change no user can see.

## Blocks

`text-size-lint-rule`.

## Meanwhile

The rest of the frontend clean-up epic waits behind this feature.

## Answer
