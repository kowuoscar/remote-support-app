---
id: proposal-rules-frontend-4-and-13
type: proposal
status: open
blocks: []
created: 2026-10-02
---

<!-- sdlc:template inbox-item 1 -->

## Question

Two coding rules don't match how the app is built. Amend them?
- **Frontend 4** says mutations go through Server Actions. Nothing uses
  them: every change goes from the browser through a frontend route to the
  backend.
- **Frontend 13** says route handlers use "one shared response helper". There
  are now two (`forwardBinary`, `forwardSecretJson`), and about ten routes
  still build their own response.

## Recommendation

Amend both to describe the real pattern:
- **Frontend 4:** mutations go through a frontend API route to the backend.
- **Frontend 13:** route handlers reuse a shared forwarder where one fits.

Leave the remaining hand-written routes as debt.

## Blocks

Nothing.

## Meanwhile

The loop carries on.

## Answer
