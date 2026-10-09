---
id: approve-package-layout
type: approval
status: open
blocks: [package-by-feature]
created: 2026-10-09
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the backend layout proposed in
`docs/adr/0006-backend-is-packaged-by-feature-flat.md`? The one-screen
summary is section 1 of `docs/features/package-layout-decision/research.md`.
Answer **approve**, **amend: …** or **reject**.

**The layout.** One folder per feature (carrier, fleet, request, invoice,
login and so on), each holding that feature's data, screens' API and logic
together. There are no layer folders inside a feature. A few shared pieces
get their own folders: `tenant`, `shared` (country, currency, billing month)
and `system` (the health check). Spring Modulith and hexagonal were studied
and rejected. Modulith refuses circular links between features, and this code
has them almost everywhere.

**The cost.** Eight features after this one:
1. add an automatic layout check;
2. move carriers;
3. move fleet and stock;
4. move requests and fees;
5. move agents, clients, testers and contracts;
6. move invoicing;
7. untangle the three invoicing services that call each other;
8. move sign-in and the shared pieces.

217 of 222 classes change folder. Nothing changes for users, and the app
stays working after each step.

**One new dependency:** ArchUnit 1.4.2, used only in tests, free (Apache 2.0).
Nothing new ships in the app.

These are choices made without you; approving accepts them:
1. **A list of exceptions that can grow.** The layout check starts with a
   list of today's exceptions. Normally the list only shrinks, but a feature
   that adds code to a part not yet moved adds its one entry. Without that,
   features such as month-close and invoice-adjustment couldn't be built
   until invoicing has moved.
2. **`shared` and `system` aren't domain words.** They hold technical pieces,
   not a feature, so they're named for what they are.
3. **The packaging rule (Backend 13) is rewritten** to match the ADR. It only
   takes effect once you approve and the layout check exists. On your
   approve, I add "Amended by the human" to it.
4. **The epic's move list is rewritten** to the eight features above.

## Recommendation

Approve. Every claim cites a source or a line of this codebase, and the
features in flight keep working through the move.

## Blocks

The moves in `package-by-feature`. The next feature, `package-boundary-check`,
doesn't start until you answer.

## Meanwhile

The other epics continue.

## Answer
