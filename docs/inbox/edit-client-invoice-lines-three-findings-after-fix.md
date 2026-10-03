---
id: edit-client-invoice-lines-three-findings-after-fix
type: question
status: answered
blocks: [edit-client-invoice-lines]
created: 2026-10-02
---

<!-- sdlc:template inbox-item 1 -->

## Question

Invoice line editing is built and every original problem is fixed and
re-checked (all tests green). But the design re-check of the fix found three
small keyboard and screen-reader problems on the Agent's Edit control (F21,
F22, F23 in `docs/features/edit-client-invoice-lines/findings.json`). The
loop allows one fix pass, so you decide.

**F21: focus blinks away while saving.** A keyboard user presses Reset (or
tabs to Save and presses Enter). For the half-second the save takes, the
button greys itself out and keyboard focus jumps to the top of the page, then
comes back. A Tab pressed in that half-second starts from the top. The cause
is the app's shared button, which the password dialogs also have (already in
`docs/tech-debt.md`).

**F22: Escape only works in the amount box.** With focus on Cancel or Save,
Escape does nothing. One-line fix.

**F23: two identical labels in one rare case.** Two Fees of the same type,
logged the same day with no description, read out identically to a screen
reader. A long description is also read in full.

**Proposed:** authorise one extra fix pass for all three, kept inside this
Edit control: the button stays focusable while saving here (the shared button
stays as is, in the debt log); Escape works from the whole form; the label
adds the Fee's position when two would otherwise match and shortens long
descriptions. Then a fresh design re-check and the merge.

## Blocks

`edit-client-invoice-lines` (findings F21, F22, F23). `send-a-client-invoice-back`
builds on it and waits too.

## Meanwhile

The loop moves on to whatever else is unblocked.

## Answer

Ok (2026-10-03). One extra fix pass for F21, F22 and F23, kept inside the Edit control; then a fresh design re-check and the merge.
